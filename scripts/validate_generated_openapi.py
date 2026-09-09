"""Validate the code-generated OpenAPI and HTTP response examples from integration tests."""

import argparse
import json
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker
from openapi_spec_validator import validate


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--spec", type=Path, default=Path("target/generated-openapi/openapi.json"))
    parser.add_argument("--examples", type=Path, default=Path("target/generated-openapi/examples"))
    args = parser.parse_args()

    specification = json.loads(args.spec.read_text(encoding="utf-8"))
    validate(specification)
    examples = {
        "signup-success.json": "UserSignupResponseDTO",
        "signup-conflict.json": "ApiErrorResponseDTO",
        "signup-invalid.json": "ApiErrorResponseDTO",
    }
    for filename, schema_name in examples.items():
        schema = {
            "$ref": f"#/components/schemas/{schema_name}",
            "components": specification["components"],
        }
        response = json.loads((args.examples / filename).read_text(encoding="utf-8"))
        Draft202012Validator(schema, format_checker=FormatChecker()).validate(response)
    print(f"OpenAPI {specification['openapi']} valid; {len(examples)} HTTP response examples match their schemas.")


if __name__ == "__main__":
    main()
