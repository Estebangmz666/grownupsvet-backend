"""Exercise CI failures on temporary copies without altering delivery artifacts."""

import copy
import json
import subprocess
import sys
import tempfile
import xml.etree.ElementTree as ElementTree
from pathlib import Path


ROOT = Path(__file__).resolve().parent.parent
GATES = ROOT / "scripts/check_ci_outputs.py"


def expect_exit(arguments: list[str], succeeds: bool, expected_error: str = "") -> None:
    result = subprocess.run([sys.executable, *map(str, arguments)], cwd=ROOT, capture_output=True, text=True)
    if (result.returncode == 0) != succeeds:
        raise RuntimeError(f"Unexpected exit {result.returncode} from {Path(arguments[0]).name}; expected {'success' if succeeds else 'failure'}.")
    if expected_error and expected_error not in result.stdout + result.stderr:
        raise RuntimeError(f"{Path(arguments[0]).name} failed for an unexpected reason.")


def main() -> None:
    contract = json.loads((ROOT / "target/generated-openapi/openapi.json").read_text(encoding="utf-8"))
    with tempfile.TemporaryDirectory(prefix="grownupsvet-ci-gates-") as directory:
        temporary = Path(directory)
        original = temporary / "original.json"
        changed = temporary / "changed.json"
        original.write_text(json.dumps(contract), encoding="utf-8")
        # Reordering object keys and JSON Schema sets must not report contract drift.
        reordered = copy.deepcopy(contract)
        for schema in reordered["components"]["schemas"].values():
            if "required" in schema:
                schema["required"].reverse()
        changed.write_text(json.dumps(reordered, sort_keys=True), encoding="utf-8")
        comparison = [str(GATES), "contract", "--generated", str(changed), "--snapshot", str(original)]
        expect_exit(comparison, True)
        reordered["info"]["version"] += "-controlled-drift"
        changed.write_text(json.dumps(reordered), encoding="utf-8")
        expect_exit(comparison, False, "differs from the versioned snapshot")

        invalid = copy.deepcopy(contract)
        invalid["openapi"] = "INVALID"
        changed.write_text(json.dumps(invalid), encoding="utf-8")
        expect_exit([str(ROOT / "scripts/validate_generated_openapi.py"), "--spec", str(changed),
                     "--examples", str(ROOT / "target/generated-openapi/examples")], False, "ValidatorDetectError")

        sources = temporary / "sources"
        reports = temporary / "reports"
        sources.mkdir()
        reports.mkdir()
        (sources / "ControlledTests.java").write_text("class ControlledTests { @Test void controlled() {} }", encoding="utf-8")
        report = ElementTree.Element("testsuite", name="ControlledTests", tests="1", failures="0", errors="0", skipped="0")
        ElementTree.SubElement(report, "testcase", name="controlled", classname="ControlledTests")
        report_file = reports / "TEST-ControlledTests.xml"
        arguments = [str(GATES), "reports", "--sources", str(sources), "--reports", str(reports),
                     "--sanitized", str(temporary / "sanitized")]
        ElementTree.ElementTree(report).write(report_file)
        expect_exit(arguments, True)
        report.set("skipped", "1")
        ElementTree.SubElement(report.find("testcase"), "skipped")
        ElementTree.ElementTree(report).write(report_file)
        expect_exit(arguments, False, "skipped tests")
        report_file.unlink()
        expect_exit(arguments, False, "No Surefire XML reports")
    print("Controlled checks passed: harmless ordering accepted; contract drift, invalid OpenAPI, skipped tests and missing reports rejected.")


if __name__ == "__main__":
    main()
