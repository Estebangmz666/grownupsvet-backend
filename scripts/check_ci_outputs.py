"""Fail CI when test evidence is incomplete or the generated contract has drifted."""

import argparse
import json
import re
import sys
import xml.etree.ElementTree as ElementTree
from pathlib import Path


def check_test_reports(reports: Path, sources: Path, sanitized: Path) -> None:
    files = sorted(reports.glob("TEST-*.xml"))
    if not files:
        raise ValueError("No Surefire XML reports were produced.")
    expected = {
        ".".join(path.relative_to(sources).with_suffix("").parts)
        for path in sources.rglob("*.java")
        if (path.stem.endswith(("Test", "Tests", "TestCase")) or path.stem.startswith("Test"))
        and re.search(r"@(Test|RepeatedTest|ParameterizedTest|TestFactory|TestTemplate)\b", path.read_text(encoding="utf-8"))
    }
    if not expected:
        raise ValueError("No test source classes were found.")
    observed = set()
    total = 0
    problems = []
    sanitized.mkdir(parents=True, exist_ok=True)
    for file in files:
        root = ElementTree.parse(file).getroot()
        if root.tag != "testsuite":
            raise ValueError(f"Unexpected Surefire XML root in {file.name}.")
        name = root.attrib.get("name", "")
        if not name or name in observed:
            raise ValueError(f"Missing or duplicate suite name in {file.name}.")
        observed.add(name)
        counts = {field: int(root.attrib[field]) for field in ("tests", "failures", "errors", "skipped")}
        cases = root.findall("testcase")
        if counts["tests"] <= 0 or counts["tests"] != len(cases):
            problems.append(f"{name}: missing test cases or inconsistent test count")
        if any(counts[field] for field in ("failures", "errors", "skipped")) or any(
            case.find(field) is not None for case in cases for field in ("failure", "error", "skipped")
        ):
            problems.append(f"{name}: failed, erroneous or skipped tests")
        total += counts["tests"]
        # The artifact retains JUnit results, without process properties or captured logs.
        for parent in root.iter():
            for child in list(parent):
                if child.tag in {"properties", "system-out", "system-err"}:
                    parent.remove(child)
        ElementTree.ElementTree(root).write(sanitized / file.name, encoding="utf-8", xml_declaration=True)
    missing = expected - observed
    if missing:
        problems.append("Missing test suites: " + ", ".join(sorted(missing)))
    if problems:
        raise ValueError("; ".join(problems))
    print(f"Surefire evidence complete: {len(observed)} suites, {total} tests; none failed or skipped.")


def normalize_contract(value, path=()):
    if isinstance(value, dict):
        return {key: normalize_contract(child, path + (key,)) for key, child in value.items()}
    if isinstance(value, list):
        children = [normalize_contract(child, path + (str(index),)) for index, child in enumerate(value)]
        # JSON Schema treats these arrays as sets. Payload/example arrays retain their order.
        in_schema = "schema" in path or path[:2] == ("components", "schemas")
        if in_schema and path[-1:] in (("required",), ("enum",), ("type",)) and not {"example", "examples", "default"}.intersection(path):
            return sorted(children, key=lambda child: json.dumps(child, sort_keys=True))
        return children
    return value


def check_contract(generated: Path, snapshot: Path) -> None:
    documents = [json.loads(path.read_text(encoding="utf-8-sig")) for path in (generated, snapshot)]
    if any(not isinstance(document, dict) or "openapi" not in document or "paths" not in document for document in documents):
        raise ValueError("Both contract files must contain an OpenAPI document.")
    canonical = [json.dumps(normalize_contract(document), sort_keys=True, ensure_ascii=False) for document in documents]
    if canonical[0] != canonical[1]:
        raise ValueError("Generated OpenAPI differs from the versioned snapshot; review the change and regenerate docs/openapi/openapi.json.")
    print("Generated OpenAPI matches the versioned snapshot (object and schema-set ordering ignored).")


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    reports = commands.add_parser("reports", help="Require complete, passing Surefire evidence.")
    reports.add_argument("--reports", type=Path, default=Path("target/surefire-reports"))
    reports.add_argument("--sources", type=Path, default=Path("src/test/java"))
    reports.add_argument("--sanitized", type=Path, default=Path("target/ci-reports"))
    contract = commands.add_parser("contract", help="Compare the generated and versioned contracts.")
    contract.add_argument("--generated", type=Path, default=Path("target/generated-openapi/openapi.json"))
    contract.add_argument("--snapshot", type=Path, default=Path("docs/openapi/openapi.json"))
    args = parser.parse_args()
    try:
        if args.command == "reports":
            check_test_reports(args.reports, args.sources, args.sanitized)
        else:
            check_contract(args.generated, args.snapshot)
    except (OSError, ValueError, KeyError, ElementTree.ParseError) as failure:
        print(f"CI evidence check failed: {failure}", file=sys.stderr)
        raise SystemExit(1) from None


if __name__ == "__main__":
    main()
