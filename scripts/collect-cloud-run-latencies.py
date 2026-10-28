"""Correlate sanitized harness attempts with existing Cloud Run request logs.

Only gcloud logging read is executed. No business request, SQL, SMTP, login,
deployment, resource creation or logging configuration is performed.
"""

import argparse
from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from decimal import Decimal, InvalidOperation
import hashlib
import json
from pathlib import Path
import os
import re
import subprocess
import sys
import tempfile
import time


PROJECT = "project-4f1c67ce-5621-40e0-96c"
SERVICE = "grownupsvet-backend"
REGION = "us-central1"
TRACE_PREFIX = f"projects/{PROJECT}/traces/"
REQUEST_LOG = f"projects/{PROJECT}/logs/run.googleapis.com%2Frequests"
PROJECTION = (
    "trace", "httpRequest.latency", "httpRequest.status", "httpRequest.requestMethod",
    "timestamp", "resource.labels.revision_name",
)
METRICS = frozenset(("M3", "M6", "M9"))
METHODS = frozenset(("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS"))
BATCH_SIZE = 50
MAX_SAMPLES = 10000
WINDOW_MARGIN = timedelta(seconds=120)
MAX_QUERY_WINDOW = timedelta(days=7)
TRACE_PATTERN = re.compile(r"[0-9a-f]{32}")
TECHNICAL_NAME = re.compile(r"[A-Za-z][A-Za-z0-9_.:-]{0,119}")
UTC_PATTERN = re.compile(r"\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,9})?(?:Z|\+00:00)")
DURATION_PATTERN = re.compile(r"(?:0|[1-9]\d{0,11})(?:\.\d{1,9})?s")


class CollectorError(Exception):
    """A fixed, safe public error code; never wrap provider diagnostics."""


def require(condition, code):
    if not condition:
        raise CollectorError(code)


def validate_revision(value):
    require(isinstance(value, str) and len(value) <= 63
            and re.fullmatch(r"grownupsvet-backend-[a-z0-9](?:[a-z0-9-]*[a-z0-9])?", value),
            "INVALID_EXPECTED_REVISION")
    return value


def validate_trace(value):
    require(isinstance(value, str) and TRACE_PATTERN.fullmatch(value)
            and value != "0" * 32, "INVALID_TRACE_ID")
    return value


def parse_utc(value):
    require(isinstance(value, str) and UTC_PATTERN.fullmatch(value), "INVALID_UTC_TIMESTAMP")
    try:
        return datetime.fromisoformat(value.replace("Z", "+00:00"))
    except ValueError:
        raise CollectorError("INVALID_UTC_TIMESTAMP") from None


def format_utc(value):
    return value.astimezone(timezone.utc).isoformat(timespec="microseconds").replace("+00:00", "Z")


def decimal_value(value, code):
    require(not isinstance(value, bool) and isinstance(value, (int, float, Decimal, str)), code)
    try:
        number = Decimal(str(value))
    except InvalidOperation:
        raise CollectorError(code) from None
    require(number.is_finite() and number >= 0, code)
    return number


def decimal_text(value):
    text = format(value, "f")
    return text.rstrip("0").rstrip(".") if "." in text else text


def duration_milliseconds(value):
    require(isinstance(value, str) and DURATION_PATTERN.fullmatch(value), "INVALID_PROVIDER_DURATION")
    seconds = decimal_value(value[:-1], "INVALID_PROVIDER_DURATION")
    require(seconds <= Decimal("315576000000"), "INVALID_PROVIDER_DURATION")
    return seconds * Decimal(1000)


def nearest_rank_p95(values):
    if not values:
        return None
    ordered = sorted(values)
    # Integer arithmetic implements ceil(0.95*N) without floating-point rounding.
    return ordered[(95 * len(ordered) + 99) // 100 - 1]


def http_status(value):
    return type(value) is int and 100 <= value <= 599


@dataclass(frozen=True)
class Attempt:
    trace_id: str
    case: str
    method: str
    status: object
    expected: tuple
    started: datetime
    completed: datetime
    client_milliseconds: Decimal
    operation: object
    metric: object
    eligible: bool
    transport: object

    @property
    def client_error(self):
        return self.transport is not None or self.status not in self.expected


def parse_report(report, expected_revision):
    validate_revision(expected_revision)
    require(isinstance(report, dict) and type(report.get("measurementSchemaVersion")) is int
            and report["measurementSchemaVersion"] == 1, "UNSUPPORTED_MEASUREMENT_SCHEMA")
    require(report.get("revision") in (None, expected_revision), "REPORT_REVISION_MISMATCH")
    samples = report.get("requestSamples")
    require(isinstance(samples, list) and len(samples) <= MAX_SAMPLES, "INVALID_REQUEST_SAMPLES")
    attempts, observed = [], set()
    for sample in samples:
        require(isinstance(sample, dict), "INVALID_ATTEMPT")
        trace = validate_trace(sample.get("traceId"))
        require(sample.get("attemptId") == trace, "ATTEMPT_TRACE_IDENTITY_MISMATCH")
        require(trace not in observed, "DUPLICATE_ATTEMPT_TRACE")
        observed.add(trace)
        require(sample.get("revision") == expected_revision, "ATTEMPT_REVISION_MISMATCH")
        case = sample.get("case")
        require(isinstance(case, str) and TECHNICAL_NAME.fullmatch(case), "INVALID_CASE_NAME")
        method = sample.get("method")
        require(isinstance(method, str) and method in METHODS, "INVALID_HTTP_METHOD")
        status, transport = sample.get("status"), sample.get("transport")
        require(transport in (None, "TRANSPORT_UNCERTAIN"), "INVALID_TRANSPORT_METADATA")
        require(http_status(status) or (status is None and transport == "TRANSPORT_UNCERTAIN"), "INVALID_CLIENT_STATUS")
        expected = sample.get("expected")
        require(isinstance(expected, list) and 0 < len(expected) <= 10
                and all(http_status(item) for item in expected), "INVALID_EXPECTED_STATUSES")
        started, completed = parse_utc(sample.get("startedAtUtc")), parse_utc(sample.get("completedAtUtc"))
        require(started <= completed and completed - started <= MAX_QUERY_WINDOW, "INVALID_ATTEMPT_WINDOW")
        elapsed = decimal_value(sample.get("elapsedMilliseconds"), "INVALID_CLIENT_DURATION")
        eligible, metric, operation = (sample.get("backendLatencyEligible"), sample.get("backendMetric"),
                                       sample.get("latencyOperation"))
        require(type(eligible) is bool, "EXPLICIT_ELIGIBILITY_REQUIRED")
        if eligible:
            require(isinstance(metric, str) and metric in METRICS and isinstance(operation, str)
                    and TECHNICAL_NAME.fullmatch(operation), "INVALID_ELIGIBLE_OPERATION")
        else:
            require(metric is None and operation is None, "INCONSISTENT_INELIGIBLE_OPERATION")
        attempts.append(Attempt(trace, case, method, status, tuple(expected), started, completed,
                                elapsed, operation, metric, eligible, transport))
    return attempts


def query_window(attempts):
    require(bool(attempts), "EMPTY_QUERY_BATCH")
    start = min(attempt.started for attempt in attempts) - WINDOW_MARGIN
    end = max(attempt.completed for attempt in attempts) + WINDOW_MARGIN
    require(end - start <= MAX_QUERY_WINDOW, "QUERY_WINDOW_TOO_WIDE")
    return start, end


def build_filter(expected_revision, traces, start, end):
    validate_revision(expected_revision)
    require(0 < len(traces) <= BATCH_SIZE and len(set(traces)) == len(traces), "INVALID_TRACE_BATCH")
    for trace in traces:
        validate_trace(trace)
    require(start.tzinfo is not None and end.tzinfo is not None
            and start.utcoffset() == timedelta(0) and end.utcoffset() == timedelta(0)
            and start <= end and end - start <= MAX_QUERY_WINDOW, "INVALID_QUERY_WINDOW")
    clauses = [
        'resource.type="cloud_run_revision"', f'resource.labels.project_id="{PROJECT}"',
        f'resource.labels.service_name="{SERVICE}"', f'resource.labels.location="{REGION}"',
        f'resource.labels.revision_name="{expected_revision}"', f'logName="{REQUEST_LOG}"',
        f'timestamp>="{format_utc(start)}"', f'timestamp<="{format_utc(end)}"',
        "(" + " OR ".join(f'trace="{TRACE_PREFIX}{trace}"' for trace in traces) + ")",
    ]
    return " AND ".join(clauses)


def flags_for_query(limit):
    return {"--project": PROJECT, "--format": "json(" + ",".join(PROJECTION) + ")",
            "--limit": limit, "--order": "asc", "--quiet": True, "--verbosity": "error"}


def gcloud_command(executable, log_filter, flags_file, platform=None):
    """Keep LOG_FILTER positional; never send it through cmd or global --filter."""
    path = Path(executable).expanduser().resolve()
    require(path.is_file() and path.name.lower() in ("gcloud", "gcloud.exe", "gcloud.cmd"), "INVALID_GCLOUD_PATH")
    windows = os.name == "nt" if platform is None else platform == "nt"
    if path.suffix.lower() == ".cmd":
        require(windows, "WINDOWS_SDK_REQUIRED")
        sdk = path.parent.parent
        entrypoint, python = sdk / "lib/gcloud.py", sdk / "platform/bundledpython/python.exe"
        require(entrypoint.is_file() and python.is_file(), "SDK_BUNDLED_PYTHON_REQUIRED")
        # Isolated mode also excludes user site-packages/PYTHONPATH; gcloud.py
        # explicitly adds its own SDK and vendored dependencies to sys.path.
        prefix = [str(python), "-I", str(entrypoint)]
    else:
        prefix = [str(path)]
    return prefix + ["logging", "read", log_filter, "--flags-file=" + str(flags_file)]


def reject_json_constant(_value):
    raise CollectorError("INVALID_JSON_NUMBER")


class GcloudReader:
    def __init__(self, executable, runner=subprocess.run):
        self.executable = executable
        self.runner = runner

    def read(self, log_filter, limit):
        # Only public query metadata goes into this temporary flags file.
        with tempfile.TemporaryDirectory(prefix="grownupsvet-latencies-") as directory:
            flags_file = Path(directory) / "logging-read.json"
            flags_file.write_text(json.dumps(flags_for_query(limit)), encoding="utf-8")
            command = gcloud_command(self.executable, log_filter, flags_file)
            environment = dict(os.environ)
            environment.update({"CLOUDSDK_CORE_DISABLE_PROMPTS": "1", "CLOUDSDK_CORE_LOG_HTTP": "false",
                                "CLOUDSDK_CORE_VERBOSITY": "error", "CLOUDSDK_CORE_DISABLE_FILE_LOGGING": "true",
                                "CLOUDSDK_CORE_DISABLE_USAGE_REPORTING": "true"})
            try:
                result = self.runner(command, capture_output=True, text=True, encoding="utf-8", errors="strict",
                                     timeout=45, shell=False, env=environment,
                                     creationflags=getattr(subprocess, "CREATE_NO_WINDOW", 0))
            except subprocess.TimeoutExpired:
                raise CollectorError("PROVIDER_READ_TIMEOUT") from None
            except (OSError, UnicodeError):
                raise CollectorError("PROVIDER_READ_UNAVAILABLE") from None
            require(result.returncode == 0, "PROVIDER_READ_FAILED")
            require(len(result.stdout) <= 2_000_000, "PROVIDER_RESPONSE_TOO_LARGE")
            try:
                rows = json.loads(result.stdout, parse_float=Decimal, parse_constant=reject_json_constant)
            except (ValueError, TypeError):
                raise CollectorError("INVALID_PROVIDER_JSON") from None
            require(isinstance(rows, list), "INVALID_PROVIDER_RESPONSE")
            return rows


def projected_trace(row):
    require(isinstance(row, dict) and set(row) <= {"trace", "httpRequest", "timestamp", "resource"},
            "PROVIDER_PROJECTION_NOT_RESPECTED")
    http, resource = row.get("httpRequest", {}), row.get("resource", {})
    require(isinstance(http, dict) and set(http) <= {"latency", "status", "requestMethod"}
            and isinstance(resource, dict) and set(resource) <= {"labels"}, "PROVIDER_PROJECTION_NOT_RESPECTED")
    labels = resource.get("labels", {})
    require(isinstance(labels, dict) and set(labels) <= {"revision_name"}, "PROVIDER_PROJECTION_NOT_RESPECTED")
    trace = row.get("trace")
    require(isinstance(trace, str) and trace.startswith(TRACE_PREFIX), "PROVIDER_TRACE_PROJECT_MISMATCH")
    identifier = trace[len(TRACE_PREFIX):]
    validate_trace(identifier)
    return identifier


def correlate_row(attempt, rows, revision):
    if not rows:
        return {"state": "NOT_VERIFIED", "reason": "MISSING_REQUEST_LOG", "providerRows": 0}
    if len(rows) != 1:
        return {"state": "NOT_VERIFIED", "reason": "DUPLICATE_REQUEST_LOG", "providerRows": len(rows)}
    row = rows[0]
    http = row.get("httpRequest", {})
    if row.get("resource", {}).get("labels", {}).get("revision_name") != revision:
        reason = "PROVIDER_REVISION_MISMATCH"
    elif http.get("requestMethod") != attempt.method:
        reason = "PROVIDER_METHOD_MISMATCH"
    elif not http_status(http.get("status")):
        reason = "INVALID_PROVIDER_STATUS"
    elif attempt.status is not None and http["status"] != attempt.status:
        reason = "PROVIDER_STATUS_MISMATCH"
    else:
        try:
            timestamp = parse_utc(row.get("timestamp"))
            require(attempt.started - WINDOW_MARGIN <= timestamp <= attempt.completed + WINDOW_MARGIN,
                    "PROVIDER_TIMESTAMP_OUTSIDE_ATTEMPT_WINDOW")
            milliseconds = duration_milliseconds(http.get("latency"))
        except CollectorError as failure:
            return {"state": "NOT_VERIFIED", "reason": str(failure), "providerRows": 1}
        return {"state": "VERIFIED", "providerRows": 1, "serverMilliseconds": milliseconds,
                "serverStatus": http["status"], "logTimestampUtc": row["timestamp"],
                "clientStatusUnobserved": attempt.status is None}
    return {"state": "NOT_VERIFIED", "reason": reason, "providerRows": 1}


def collect(report, expected_revision, reader, read_attempts=3, retry_delay_seconds=10, sleeper=time.sleep):
    require(type(read_attempts) is int and 1 <= read_attempts <= 4, "INVALID_READ_ATTEMPTS")
    require(type(retry_delay_seconds) is int and 0 <= retry_delay_seconds <= 20, "INVALID_RETRY_DELAY")
    attempts = parse_report(report, expected_revision)
    eligible = [attempt for attempt in attempts if attempt.eligible]
    outcomes, queries = {}, []
    for offset in range(0, len(eligible), BATCH_SIZE):
        pending = eligible[offset:offset + BATCH_SIZE]
        for read_number in range(1, read_attempts + 1):
            start, end = query_window(pending)
            traces = [attempt.trace_id for attempt in pending]
            limit = 2 * len(traces) + 1
            query = {"traceCount": len(traces), "readNumber": read_number, "rowLimit": limit,
                     "windowStartUtc": format_utc(start), "windowEndUtc": format_utc(end)}
            try:
                rows = reader.read(build_filter(expected_revision, traces, start, end), limit)
                require(isinstance(rows, list), "INVALID_PROVIDER_RESPONSE")
                require(len(rows) < limit, "PROVIDER_ROW_LIMIT_REACHED")
                indexed = defaultdict(list)
                for row in rows:
                    trace = projected_trace(row)
                    require(trace in traces, "PROVIDER_TRACE_OUTSIDE_BATCH")
                    indexed[trace].append(row)
                query.update({"returnedRows": len(rows), "outcome": "READ"})
                for attempt in pending:
                    outcomes[attempt.trace_id] = correlate_row(attempt, indexed[attempt.trace_id], expected_revision)
            except CollectorError as failure:
                query.update({"outcome": "NOT_VERIFIED", "reason": str(failure)})
                for attempt in pending:
                    outcomes[attempt.trace_id] = {"state": "NOT_VERIFIED", "reason": str(failure)}
            queries.append(query)
            pending = [attempt for attempt in pending
                       if outcomes[attempt.trace_id].get("reason") == "MISSING_REQUEST_LOG"]
            if not pending or read_number == read_attempts:
                break
            sleeper(retry_delay_seconds)

    groups = defaultdict(list)
    for attempt in eligible:
        groups[(attempt.metric, attempt.operation)].append(attempt)
    operations = []
    for (metric, operation), members in sorted(groups.items()):
        durations = [outcomes[item.trace_id]["serverMilliseconds"] for item in members
                     if outcomes[item.trace_id]["state"] == "VERIFIED"]
        complete = len(durations) == len(members)
        client_values = [item.client_milliseconds for item in members]
        operations.append({
            "metric": metric, "operation": operation, "eligibleAttempts": len(members),
            "correlatedAttempts": len(durations), "unverifiedAttempts": len(members) - len(durations),
            "correlationComplete": complete, "p95Milliseconds": nearest_rank_p95(durations) if complete else None,
            "observedSubsetP95Milliseconds": nearest_rank_p95(durations),
            "clientP95MillisecondsEndToEnd": nearest_rank_p95(client_values),
            "clientErrorsOrTimeouts": sum(item.client_error for item in members),
            "clientTimeouts": sum(item.transport is not None for item in members),
            "clientDurationsIncludeCensoredTimeouts": any(item.transport is not None for item in members),
            "serverErrorResponses": sum(outcomes[item.trace_id].get("serverStatus", 0) >= 500 for item in members),
            "assessment": "CORRELATED_SAMPLE_ONLY" if complete else "NOT_VERIFIED_INCOMPLETE_CORRELATION",
        })
    samples = []
    for attempt in attempts:
        sample = {"attemptId": attempt.trace_id, "traceId": attempt.trace_id, "case": attempt.case,
                  "method": attempt.method, "status": attempt.status, "transport": attempt.transport,
                  "startedAtUtc": format_utc(attempt.started), "completedAtUtc": format_utc(attempt.completed),
                  "clientMillisecondsEndToEnd": attempt.client_milliseconds,
                  "backendLatencyEligible": attempt.eligible, "backendMetric": attempt.metric,
                  "latencyOperation": attempt.operation,
                  "correlation": outcomes[attempt.trace_id] if attempt.eligible else {"state": "NOT_ELIGIBLE"}}
        samples.append(sample)
    verified = sum(outcome["state"] == "VERIFIED" for outcome in outcomes.values())
    status = "VERIFIED_FOR_DECLARED_SAMPLE" if eligible and verified == len(eligible) else "NOT_VERIFIED"
    return {"collectorSchemaVersion": 1, "inputMeasurementSchemaVersion": 1,
            "collectedAtUtc": format_utc(datetime.now(timezone.utc)), "status": status,
            "measurement": "Cloud Run request latency; not exclusive Java execution time",
            "project": PROJECT, "service": SERVICE, "region": REGION, "expectedRevision": expected_revision,
            "providerCommand": "gcloud logging read", "projection": list(PROJECTION),
            "windowMarginSeconds": int(WINDOW_MARGIN.total_seconds()), "requestCount": len(attempts),
            "eligibleAttempts": len(eligible), "correlatedAttempts": verified,
            "unverifiedAttempts": len(eligible) - verified, "operations": operations,
            "requestSamples": samples, "queries": queries,
            "limitations": [
                "Only explicitly eligible attempts in the input report are measured; no population or SLO approval.",
                "Missing, duplicated or incompatible logs remain in the denominator and prevent a complete-sample P95.",
                "Client end-to-end times include the network; client timeouts are censored lower bounds.",
                "A server log after a client timeout does not prove client success or business persistence.",
                "Correlation is a point-in-time observation of existing request logs, not a business retry.",
            ]}


class SafeArgumentParser(argparse.ArgumentParser):
    def error(self, _message):
        raise CollectorError("INVALID_ARGUMENTS")


def json_decimal(value):
    if isinstance(value, Decimal):
        return decimal_text(value)
    raise TypeError("Unsupported JSON value")


def main(argv=None):
    parser = SafeArgumentParser(description=__doc__)
    parser.add_argument("--report", type=Path, required=True)
    parser.add_argument("--expected-revision", required=True)
    parser.add_argument("--gcloud-path", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--read-attempts", type=int, default=3)
    parser.add_argument("--retry-delay-seconds", type=int, default=10)
    try:
        arguments = parser.parse_args(argv)
        source, output = arguments.report.resolve(), arguments.output.resolve()
        require(source != output, "OUTPUT_MUST_DIFFER_FROM_INPUT")
        require(not output.exists(), "OUTPUT_ALREADY_EXISTS")
        require(source.is_file() and source.stat().st_size <= 20_000_000, "INVALID_REPORT_FILE")
        source_bytes = source.read_bytes()
        raw = source_bytes.decode("utf-8-sig")
        try:
            report = json.loads(raw, parse_float=Decimal, parse_constant=reject_json_constant)
        except ValueError:
            raise CollectorError("INVALID_REPORT_JSON") from None
        result = collect(report, arguments.expected_revision, GcloudReader(arguments.gcloud_path),
                         arguments.read_attempts, arguments.retry_delay_seconds)
        result["sourceReportSha256"] = hashlib.sha256(source_bytes).hexdigest()
        # Exclusive creation preserves every earlier observation and the original harness report.
        with output.open("x", encoding="utf-8", newline="\n") as destination:
            destination.write(json.dumps(result, indent=2, ensure_ascii=False, default=json_decimal) + "\n")
        print(json.dumps({"status": result["status"], "eligibleAttempts": result["eligibleAttempts"],
                          "correlatedAttempts": result["correlatedAttempts"],
                          "unverifiedAttempts": result["unverifiedAttempts"]}))
        return 0 if result["status"] == "VERIFIED_FOR_DECLARED_SAMPLE" else 2
    except CollectorError as failure:
        print(json.dumps({"status": "FAILED", "errorCode": str(failure)}))
        return 1
    except (OSError, ValueError, TypeError, OverflowError):
        print(json.dumps({"status": "FAILED", "errorCode": "LOCAL_INPUT_OR_OUTPUT_FAILED"}))
        return 1


if __name__ == "__main__":
    raise SystemExit(main())
