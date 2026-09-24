"""Validate the code-generated OpenAPI and HTTP response examples from integration tests."""

import argparse
import json
from pathlib import Path

from jsonschema import Draft202012Validator, FormatChecker
from openapi_spec_validator import validate


AVAILABILITY_REQUIRED_FIELDS = {
    "VeterinarianAvailabilitySlotResponseDTO": {
        "id", "veterinarianId", "startsAt", "endsAt", "status", "version", "timeZone",
    },
    "AvailableVeterinarianSlotResponseDTO": {
        "id", "veterinarianId", "veterinarianFullName", "startsAt", "endsAt", "timeZone",
    },
    "VeterinarianAvailabilityEventResponseDTO": {
        "id", "slotId", "slotVersion", "actorId", "occurredAt", "eventType",
        "previousStartsAt", "previousEndsAt", "previousStatus",
        "newStartsAt", "newEndsAt", "newStatus", "reason",
    },
    "VeterinarianAvailabilitySlotPageResponseDTO": {
        "items", "page", "size", "totalElements", "totalPages",
    },
    "AvailableVeterinarianSlotPageResponseDTO": {
        "items", "page", "size", "totalElements", "totalPages",
    },
    "VeterinarianAvailabilityEventPageResponseDTO": {
        "items", "page", "size", "totalElements", "totalPages",
    },
    "CreateVeterinarianAvailabilitySlotBatchResponseDTO": {"createdCount", "items", "timeZone"},
}


def response_validator(specification: dict, schema_name: str) -> Draft202012Validator:
    return Draft202012Validator({
        "$ref": f"#/components/schemas/{schema_name}",
        "components": specification["components"],
    }, format_checker=FormatChecker())


def validate_availability_required_fields(specification: dict, responses: dict) -> None:
    """Exercise required fields against real responses, including nullable event fields."""
    samples = {
        "VeterinarianAvailabilitySlotResponseDTO": [responses["availability-slot-created.json"]],
        "AvailableVeterinarianSlotResponseDTO": responses["available-slot-page.json"]["items"],
        "VeterinarianAvailabilityEventResponseDTO": responses["availability-event-page.json"]["items"],
        "VeterinarianAvailabilitySlotPageResponseDTO": [responses["availability-slot-page.json"]],
        "AvailableVeterinarianSlotPageResponseDTO": [responses["available-slot-page.json"]],
        "VeterinarianAvailabilityEventPageResponseDTO": [responses["availability-event-page.json"]],
        "CreateVeterinarianAvailabilitySlotBatchResponseDTO": [responses["availability-slot-batch-created.json"]],
    }
    for schema_name, required_fields in AVAILABILITY_REQUIRED_FIELDS.items():
        schema = specification["components"]["schemas"][schema_name]
        if set(schema.get("required", [])) != required_fields:
            raise AssertionError(f"{schema_name}: required fields do not match the response contract")
        validator = response_validator(specification, schema_name)
        if validator.is_valid({}):
            raise AssertionError(f"{schema_name}: an empty response must be rejected")
        if not samples[schema_name]:
            raise AssertionError(f"{schema_name}: a real response example is required")
        for response in samples[schema_name]:
            validator.validate(response)
            for field in sorted(required_fields):
                incomplete_response = {key: value for key, value in response.items() if key != field}
                if validator.is_valid(incomplete_response):
                    raise AssertionError(f"{schema_name}: omission of {field} must be rejected")
            for field, value in response.items():
                if type(value) is int:
                    for invalid_value in (str(value), value + 0.5):
                        invalid_response = {**response, field: invalid_value}
                        if validator.is_valid(invalid_response):
                            raise AssertionError(f"{schema_name}: {field} must reject non-integer values")
            for field in required_fields & {"status", "previousStatus", "newStatus", "eventType", "timeZone"}:
                if validator.is_valid({**response, field: "INVALID_ENUM_VALUE"}):
                    raise AssertionError(f"{schema_name}: {field} must reject values outside its enum")


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
        "login-success.json": "UserLoginResponseDTO",
        "login-invalid-credentials.json": "ApiErrorResponseDTO",
        "profile-success.json": "OwnerProfileResponseDTO",
        "profile-invalid.json": "ApiErrorResponseDTO",
        "pet-created.json": "PetResponseDTO",
        "pet-page.json": "PetPageResponseDTO",
        "pet-invalid.json": "ApiErrorResponseDTO",
        "recovery-request-success.json": "UserPasswordRecoveryResponseDTO",
        "recovery-verification-success.json": "UserPasswordRecoveryVerificationResponseDTO",
        "recovery-invalid-code.json": "ApiErrorResponseDTO",
        "staff-administrator-created.json": "AdministratorResponseDTO",
        "staff-veterinarian-created.json": "VeterinarianResponseDTO",
        "staff-veterinarian-public-profile.json": "VeterinarianPublicProfileResponseDTO",
        "staff-invitation-success.json": "StaffInvitationResponseDTO",
        "staff-invitation-invalid-error.json": "ApiErrorResponseDTO",
        "availability-slot-created.json": "VeterinarianAvailabilitySlotResponseDTO",
        "availability-slot-batch-created.json": "CreateVeterinarianAvailabilitySlotBatchResponseDTO",
        "availability-slot-page.json": "VeterinarianAvailabilitySlotPageResponseDTO",
        "available-slot-page.json": "AvailableVeterinarianSlotPageResponseDTO",
        "availability-event-page.json": "VeterinarianAvailabilityEventPageResponseDTO",
        "availability-invalid-error.json": "ApiErrorResponseDTO",
        "availability-conflict-error.json": "ApiErrorResponseDTO",
    }
    responses = {}
    for filename, schema_name in examples.items():
        response = json.loads((args.examples / filename).read_text(encoding="utf-8"))
        response_validator(specification, schema_name).validate(response)
        responses[filename] = response
    validate_availability_required_fields(specification, responses)
    print(f"OpenAPI {specification['openapi']} valid; {len(examples)} HTTP response examples match their schemas; "
          f"required-field, integer and enum rejection checks passed for {len(AVAILABILITY_REQUIRED_FIELDS)} availability schemas.")


if __name__ == "__main__":
    main()
