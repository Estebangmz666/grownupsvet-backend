"""Offline collector verification: synthetic logs, no CLI/network/DB execution."""

from contextlib import redirect_stdout
from copy import deepcopy
from datetime import timedelta
from decimal import Decimal
import hashlib
import importlib.util
import io
import json
from pathlib import Path
import re
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch


SCRIPT = Path(__file__).resolve().parents[1] / "collect-cloud-run-latencies.py"
SPEC = importlib.util.spec_from_file_location("cloud_run_latency_collector", SCRIPT)
collector = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = collector
SPEC.loader.exec_module(collector)
REVISION = "grownupsvet-backend-admission-960c8898"


def trace(number=1):
    return f"{number:032x}"


def sample(number=1, **changes):
    value = {"attemptId": trace(number), "traceId": trace(number), "revision": REVISION,
             "case": "read_clinical_detail", "method": "GET", "status": 200, "expected": [200],
             "startedAtUtc": "2026-10-28T14:02:00.000000Z", "completedAtUtc": "2026-10-28T14:02:01.000000Z",
             "elapsedMilliseconds": 1000, "latencyOperation": "M9_detail", "backendLatencyEligible": True,
             "backendMetric": "M9", "transport": None, "errorCode": None}
    value.update(changes)
    return value


def report(*samples):
    return {"measurementSchemaVersion": 1, "revision": REVISION, "requestSamples": list(samples)}


def row(number=1, duration="0.003294980s", status=200):
    return {"trace": collector.TRACE_PREFIX + trace(number),
            "httpRequest": {"latency": duration, "requestMethod": "GET", "status": status},
            "resource": {"labels": {"revision_name": REVISION}}, "timestamp": "2026-10-28T14:02:00.500000123Z"}


class Reader:
    def __init__(self, *responses):
        self.responses = list(responses)
        self.calls = []

    def read(self, log_filter, limit):
        self.calls.append((log_filter, limit))
        response = self.responses.pop(0) if self.responses else []
        if isinstance(response, Exception):
            raise response
        return response


class CollectorTests(unittest.TestCase):
    def collect(self, samples, rows, **options):
        reader = Reader(rows)
        return collector.collect(report(*samples), REVISION, reader, read_attempts=1,
                                 retry_delay_seconds=0, sleeper=lambda _delay: None, **options)

    def test_duration_conversion_preserves_nanoseconds_without_float_rounding(self):
        self.assertEqual(collector.duration_milliseconds("0.003294980s"), Decimal("3.294980"))
        self.assertEqual(collector.duration_milliseconds("0.000000001s"), Decimal("0.000001"))
        self.assertEqual(collector.duration_milliseconds("0s"), Decimal(0))
        for value in ("-0.001s", "NaNs", "Infinitys", "1e3s", "1.0000000001s", "315576000001s", None):
            with self.subTest(value=value), self.assertRaises(collector.CollectorError):
                collector.duration_milliseconds(value)

    def test_nearest_rank_for_zero_one_and_twenty_samples(self):
        self.assertIsNone(collector.nearest_rank_p95([]))
        self.assertEqual(collector.nearest_rank_p95([Decimal("1.000001")]), Decimal("1.000001"))
        values = [Decimal(number) / 1000000 for number in range(20, 0, -1)]
        self.assertEqual(collector.nearest_rank_p95(values), Decimal("0.000019"))

    def test_exact_correlation_keeps_client_and_server_measurements_separate(self):
        result = self.collect([sample()], [row()])
        self.assertEqual(result["status"], "VERIFIED_FOR_DECLARED_SAMPLE")
        operation = result["operations"][0]
        self.assertEqual(operation["p95Milliseconds"], Decimal("3.294980"))
        self.assertEqual(operation["clientP95MillisecondsEndToEnd"], Decimal(1000))
        self.assertEqual(result["eligibleAttempts"], 1)

    def test_missing_log_stays_in_denominator_and_blocks_complete_sample_percentile(self):
        result = self.collect([sample(1), sample(2)], [row(1)])
        operation = result["operations"][0]
        self.assertEqual((operation["eligibleAttempts"], operation["correlatedAttempts"]), (2, 1))
        self.assertIsNone(operation["p95Milliseconds"])
        self.assertEqual(operation["observedSubsetP95Milliseconds"], Decimal("3.294980"))
        self.assertEqual(result["requestSamples"][1]["correlation"]["reason"], "MISSING_REQUEST_LOG")

    def test_identical_duplicate_rows_are_not_deduplicated_or_selected(self):
        result = self.collect([sample()], [row(), row()])
        self.assertEqual(result["correlatedAttempts"], 0)
        self.assertEqual(result["requestSamples"][0]["correlation"]["reason"], "DUPLICATE_REQUEST_LOG")

    def test_wrong_method_status_revision_duration_and_timestamp_are_unverified(self):
        cases = []
        for field, value, reason in (("requestMethod", "POST", "PROVIDER_METHOD_MISMATCH"),
                                     ("status", 201, "PROVIDER_STATUS_MISMATCH"),
                                     ("status", "200", "INVALID_PROVIDER_STATUS"),
                                     ("latency", "-0.001s", "INVALID_PROVIDER_DURATION")):
            changed = row()
            changed["httpRequest"][field] = value
            cases.append((changed, reason))
        changed = row()
        changed["resource"]["labels"]["revision_name"] = "grownupsvet-backend-other"
        cases.append((changed, "PROVIDER_REVISION_MISMATCH"))
        changed = row()
        changed["timestamp"] = "2026-10-28T13:00:00Z"
        cases.append((changed, "PROVIDER_TIMESTAMP_OUTSIDE_ATTEMPT_WINDOW"))
        for changed, reason in cases:
            with self.subTest(reason=reason):
                result = self.collect([sample()], [changed])
                self.assertEqual(result["requestSamples"][0]["correlation"]["reason"], reason)
                self.assertIsNone(result["operations"][0]["p95Milliseconds"])

    def test_timeout_has_its_own_attempt_and_does_not_become_client_success(self):
        result = self.collect([sample(status=None, transport="TRANSPORT_UNCERTAIN", elapsedMilliseconds=35000)], [row()])
        self.assertEqual(result["correlatedAttempts"], 1)
        self.assertIsNone(result["requestSamples"][0]["status"])
        self.assertTrue(result["requestSamples"][0]["correlation"]["clientStatusUnobserved"])
        self.assertEqual(result["operations"][0]["clientErrorsOrTimeouts"], 1)
        self.assertTrue(result["operations"][0]["clientDurationsIncludeCensoredTimeouts"])

    def test_http_error_response_is_included_in_server_percentile(self):
        result = self.collect([sample(status=500)], [row(duration="2s", status=500)])
        self.assertEqual(result["operations"][0]["p95Milliseconds"], Decimal(2000))
        self.assertEqual(result["operations"][0]["serverErrorResponses"], 1)
        self.assertEqual(result["operations"][0]["clientErrorsOrTimeouts"], 1)

    def test_retries_query_only_missing_traces_and_do_not_count_previous_rows_twice(self):
        reader = Reader([row(1)], [row(2)])
        sleeps = []
        result = collector.collect(report(sample(1), sample(2)), REVISION, reader, sleeper=sleeps.append)
        self.assertEqual(result["correlatedAttempts"], 2)
        self.assertEqual(len(reader.calls), 2)
        self.assertNotIn(trace(1), reader.calls[1][0])
        self.assertIn(trace(2), reader.calls[1][0])
        self.assertEqual(sleeps, [10])

    def test_missing_ingestion_retries_are_bounded(self):
        reader = Reader([], [], [])
        sleeps = []
        result = collector.collect(report(sample()), REVISION, reader, sleeper=sleeps.append)
        self.assertEqual(len(reader.calls), 3)
        self.assertEqual(sleeps, [10, 10])
        self.assertEqual(result["unverifiedAttempts"], 1)

    def test_duplicates_are_not_retried_until_a_convenient_response_is_found(self):
        reader = Reader([row(), row()], [row()])
        result = collector.collect(report(sample()), REVISION, reader, sleeper=lambda _delay: self.fail("unexpected sleep"))
        self.assertEqual(len(reader.calls), 1)
        self.assertEqual(result["unverifiedAttempts"], 1)

    def test_saturated_query_fails_closed_instead_of_trusting_truncated_counts(self):
        result = self.collect([sample()], [row(), row(), row()])
        self.assertEqual(result["requestSamples"][0]["correlation"]["reason"], "PROVIDER_ROW_LIMIT_REACHED")

    def test_trace_project_prefix_and_unrequested_trace_are_rejected(self):
        changed = row()
        changed["trace"] = "projects/unapproved-project/traces/" + trace()
        for changed, reason in ((changed, "PROVIDER_TRACE_PROJECT_MISMATCH"), (row(9), "PROVIDER_TRACE_OUTSIDE_BATCH")):
            with self.subTest(reason=reason):
                result = self.collect([sample()], [changed])
                self.assertEqual(result["requestSamples"][0]["correlation"]["reason"], reason)

    def test_provider_projection_does_not_admit_url_ip_body_or_headers(self):
        changed = row()
        changed["httpRequest"]["remoteIp"] = "private-provider-value"
        changed["httpRequest"]["requestUrl"] = "https://private-provider-value/"
        result = self.collect([sample()], [changed])
        encoded = json.dumps(result, default=collector.json_decimal)
        self.assertNotIn("private-provider-value", encoded)
        self.assertEqual(result["requestSamples"][0]["correlation"]["reason"], "PROVIDER_PROJECTION_NOT_RESPECTED")

    def test_only_explicit_eligibility_is_used_and_empty_sample_has_no_percentile(self):
        excluded = sample(2, backendLatencyEligible=False, backendMetric=None, latencyOperation=None)
        excluded["case"] = "M9_name_does_not_enable_measurement"
        reader = Reader([row(1)])
        result = collector.collect(report(sample(1), excluded), REVISION, reader)
        self.assertEqual(result["eligibleAttempts"], 1)
        self.assertNotIn(trace(2), reader.calls[0][0])
        self.assertEqual(result["requestSamples"][1]["correlation"]["state"], "NOT_ELIGIBLE")
        empty_reader = Reader()
        empty = collector.collect(report(), REVISION, empty_reader)
        self.assertEqual(empty["operations"], [])
        self.assertEqual(empty["status"], "NOT_VERIFIED")
        self.assertEqual(empty_reader.calls, [])

    def test_different_metrics_and_operations_are_not_pooled(self):
        result = self.collect([sample(1), sample(2, latencyOperation="M3_pet_create", backendMetric="M3")],
                              [row(1, "0.001s"), row(2, "3s")])
        self.assertEqual([(value["metric"], value["p95Milliseconds"]) for value in result["operations"]],
                         [("M3", Decimal(3000)), ("M9", Decimal(1))])

    def test_batches_never_exceed_fifty_and_keep_all_fifty_one_attempts(self):
        reader = Reader([row(number) for number in range(1, 51)], [row(51)])
        result = collector.collect(report(*(sample(number) for number in range(1, 52))), REVISION, reader)
        self.assertEqual([query["traceCount"] for query in result["queries"]], [50, 1])
        self.assertEqual(result["correlatedAttempts"], 51)
        self.assertLessEqual(max(len(re.findall(r'trace="', call[0])) for call in reader.calls), 50)

    def test_report_guards_reject_identity_revision_method_and_metadata_corruption(self):
        bad_samples = [sample(attemptId=trace(8)), sample(revision="grownupsvet-backend-wrong"),
                       sample(traceId='" OR severity>=ERROR'), sample(traceId="0" * 32, attemptId="0" * 32),
                       sample(method="get"), sample(case="person@example.com"), sample(status=None),
                       sample(status=True), sample(backendLatencyEligible=None), sample(backendMetric="M1"),
                       sample(startedAtUtc="2026-10-28T14:02:00-05:00"), sample(elapsedMilliseconds=-1)]
        for value in bad_samples:
            with self.subTest(value=value), self.assertRaises(collector.CollectorError):
                collector.parse_report(report(value), REVISION)
        with self.assertRaisesRegex(collector.CollectorError, "DUPLICATE_ATTEMPT_TRACE"):
            collector.parse_report(report(sample(), sample()), REVISION)

    def test_filter_is_allowlisted_server_side_and_uses_exact_trace_equality(self):
        start = collector.parse_utc("2026-10-28T14:00:00Z")
        end = collector.parse_utc("2026-10-28T14:04:00Z")
        value = collector.build_filter(REVISION, [trace()], start, end)
        for fragment in (f'resource.labels.project_id="{collector.PROJECT}"',
                         f'resource.labels.service_name="{collector.SERVICE}"',
                         'resource.labels.location="us-central1"', f'resource.labels.revision_name="{REVISION}"',
                         f'logName="{collector.REQUEST_LOG}"', f'trace="{collector.TRACE_PREFIX}{trace()}"',
                         'timestamp>=', 'timestamp<='):
            self.assertIn(fragment, value)
        for bad in ('grownupsvet-backend-good" OR true', "other-service-revision", "grownupsvet-backend-" + "a" * 64):
            with self.assertRaises(collector.CollectorError):
                collector.build_filter(bad, [trace()], start, end)
        with self.assertRaises(collector.CollectorError):
            collector.build_filter(REVISION, [trace(number) for number in range(1, 52)], start, end)
        with self.assertRaises(collector.CollectorError):
            collector.build_filter(REVISION, [trace()], start, start + timedelta(days=8))

    def test_windows_cmd_path_uses_sdk_python_and_never_a_command_interpreter(self):
        with tempfile.TemporaryDirectory() as directory:
            sdk = Path(directory) / "SDK & literal arguments"
            for relative in ("bin/gcloud.cmd", "lib/gcloud.py", "platform/bundledpython/python.exe"):
                path = sdk / relative
                path.parent.mkdir(parents=True, exist_ok=True)
                path.write_text("fixture", encoding="utf-8")
            filter_value = 'resource.type="cloud_run_revision" AND (trace="fixture")'
            command = collector.gcloud_command(sdk / "bin/gcloud.cmd", filter_value, sdk / "flags with spaces.json", platform="nt")
            self.assertEqual(command[:3], [str(sdk / "platform/bundledpython/python.exe"), "-I", str(sdk / "lib/gcloud.py")])
            self.assertIn(filter_value, command)
            self.assertEqual(command[3:5], ["logging", "read"])
            self.assertNotIn("cmd.exe", " ".join(command))
            self.assertFalse(any(item.startswith("--filter") for item in command))
            (sdk / "platform/bundledpython/python.exe").unlink()
            with self.assertRaisesRegex(collector.CollectorError, "SDK_BUNDLED_PYTHON_REQUIRED"):
                collector.gcloud_command(sdk / "bin/gcloud.cmd", filter_value, sdk / "flags.json", platform="nt")

    def test_reader_uses_json_flags_projection_and_hides_provider_stderr(self):
        with tempfile.TemporaryDirectory() as directory:
            executable = Path(directory) / "gcloud.exe"
            executable.write_text("fixture", encoding="utf-8")
            seen_flags = []

            def runner(command, **options):
                self.assertFalse(options["shell"])
                self.assertEqual(options["timeout"], 45)
                self.assertEqual(options["env"]["CLOUDSDK_CORE_LOG_HTTP"], "false")
                self.assertEqual(options["env"]["CLOUDSDK_CORE_DISABLE_FILE_LOGGING"], "true")
                self.assertEqual(options["env"]["CLOUDSDK_CORE_DISABLE_USAGE_REPORTING"], "true")
                flags_path = Path(command[-1].split("=", 1)[1])
                flags = json.loads(flags_path.read_text(encoding="utf-8"))
                self.assertEqual(flags["--project"], collector.PROJECT)
                self.assertEqual(flags["--limit"], 3)
                self.assertNotIn("--filter", flags)
                self.assertEqual(flags["--format"], "json(" + ",".join(collector.PROJECTION) + ")")
                seen_flags.append(flags_path)
                return SimpleNamespace(returncode=0, stdout=json.dumps([row()]), stderr="private-authorization-diagnostic")

            captured = io.StringIO()
            with redirect_stdout(captured):
                result = collector.GcloudReader(executable, runner=runner).read("literal-server-filter", 3)
            self.assertEqual(result, [row()])
            self.assertEqual(captured.getvalue(), "")
            self.assertFalse(seen_flags[0].exists())

    def test_provider_failure_and_timeout_never_echo_raw_output(self):
        with tempfile.TemporaryDirectory() as directory:
            executable = Path(directory) / "gcloud.exe"
            executable.write_text("fixture", encoding="utf-8")
            for failure in (SimpleNamespace(returncode=1, stdout="private-json", stderr="private-secret"),
                            subprocess.TimeoutExpired(["fixture"], 45, output="private-secret")):
                def runner(_command, **_options):
                    if isinstance(failure, Exception):
                        raise failure
                    return failure
                reader = collector.GcloudReader(executable, runner=runner)
                result = collector.collect(report(sample()), REVISION, reader)
                self.assertNotIn("private-secret", json.dumps(result, default=collector.json_decimal))
                self.assertEqual(result["unverifiedAttempts"], 1)
                self.assertEqual(len(result["queries"]), 1)

    def test_main_writes_new_sanitized_artifact_and_hashes_the_input_it_parsed(self):
        with tempfile.TemporaryDirectory() as directory:
            source, output = Path(directory) / "input.json", Path(directory) / "result.json"
            source_bytes = json.dumps(report(sample())).encode()
            source.write_bytes(source_bytes)

            class MutatingFixtureReader:
                def read(self, _filter, _limit):
                    source.write_text('{"changed":"while-reading"}', encoding="utf-8")
                    return [row()]

            arguments = ["--report", str(source), "--expected-revision", REVISION,
                         "--gcloud-path", "unused-fixture", "--output", str(output)]
            with patch.object(collector, "GcloudReader", return_value=MutatingFixtureReader()), redirect_stdout(io.StringIO()):
                self.assertEqual(collector.main(arguments), 0)
            result = json.loads(output.read_text(encoding="utf-8"))
            self.assertEqual(result["sourceReportSha256"], hashlib.sha256(source_bytes).hexdigest())
            self.assertEqual(result["operations"][0]["p95Milliseconds"], "3.29498")
            original_output = output.read_bytes()
            with redirect_stdout(io.StringIO()):
                self.assertEqual(collector.main(arguments), 1)
            self.assertEqual(output.read_bytes(), original_output)

    def test_retry_configuration_is_bounded_before_any_provider_call(self):
        reader = Reader()
        for options in ({"read_attempts": 0}, {"read_attempts": 5}, {"retry_delay_seconds": 21}):
            with self.assertRaises(collector.CollectorError):
                collector.collect(report(sample()), REVISION, reader, **options)
        self.assertEqual(reader.calls, [])


if __name__ == "__main__":
    unittest.main()
