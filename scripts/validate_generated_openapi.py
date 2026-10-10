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
        "id", "veterinarianId", "veterinarianFullName", "startsAt", "endsAt", "version", "timeZone",
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

APPOINTMENT_REQUIRED_FIELDS = {
    "AppointmentResponseDTO": {
        "id", "ownerId", "ownerFullName", "ownerPhoneNumber", "petId", "petName", "veterinarianId",
        "veterinarianFullName", "status", "assignmentStatus", "reason", "startsAt", "endsAt", "version",
        "createdAt", "updatedAt", "timeZone",
    },
    "AppointmentEventResponseDTO": {
        "id", "appointmentId", "appointmentVersion", "eventType", "actorType", "occurredAt",
        "previousStatus", "newStatus", "previousAssignmentStatus", "newAssignmentStatus",
        "previousSlotId", "newSlotId", "actorId", "reason",
    },
    "AppointmentPageResponseDTO": {"items", "page", "size", "totalElements", "totalPages"},
    "AppointmentEventPageResponseDTO": {"items", "page", "size", "totalElements", "totalPages"},
    "OwnerEmailResponseDTO": {"appointmentId", "email"},
}


def response_validator(specification: dict, schema_name: str) -> Draft202012Validator:
    return Draft202012Validator({
        "$ref": f"#/components/schemas/{schema_name}",
        "components": specification["components"],
    }, format_checker=FormatChecker())


def validate_enum_fields(validator: Draft202012Validator, schema_name: str, response: dict, fields: set) -> None:
    """Check enum fields even when their schemas use a component reference."""
    enum_fields = {"status", "assignmentStatus", "previousStatus", "newStatus", "previousAssignmentStatus",
                   "newAssignmentStatus", "eventType", "actorType", "timeZone"}
    for field in fields & enum_fields:
        if validator.is_valid({**response, field: "INVALID_ENUM_VALUE"}):
            raise AssertionError(f"{schema_name}: {field} must reject values outside its enum")


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
            validate_enum_fields(validator, schema_name, response, required_fields)
            if validator.is_valid({**response, "unexpectedField": "unexpected"}):
                raise AssertionError(f"{schema_name}: unknown response fields must be rejected")
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
            for field, property_schema in schema["properties"].items():
                nullable = "null" in property_schema.get("type", [])
                if validator.is_valid({**response, field: None}) != nullable:
                    raise AssertionError(f"{schema_name}: {field} nullable contract is inconsistent")
                if "enum" in property_schema and validator.is_valid({**response, field: "INVALID_ENUM_VALUE"}):
                    raise AssertionError(f"{schema_name}: {field} must reject values outside its enum")


def validate_appointment_operation_responses(specification: dict) -> None:
    expected = {
        ("/api/v1/appointments", "post"): {"200", "201", "400", "401", "403", "404", "409", "500"},
        ("/api/v1/appointments", "get"): {"200", "400", "401", "403", "500"},
        ("/api/v1/appointments/{appointmentId}", "get"): {"200", "400", "401", "403", "404", "500"},
        ("/api/v1/appointments/{appointmentId}/events", "get"): {"200", "400", "401", "403", "404", "500"},
        ("/api/v1/appointments/{appointmentId}/status", "patch"): {"200", "400", "401", "403", "404", "409", "500"},
        ("/api/v1/appointments/{appointmentId}/veterinarian-reassignments", "post"): {"200", "400", "401", "403", "404", "409", "500"},
        ("/api/v1/appointments/{appointmentId}/reschedulings", "post"): {"200", "400", "401", "403", "404", "409", "500"},
        ("/api/v1/appointments/{appointmentId}/owner-email-accesses", "post"): {"200", "400", "401", "403", "404", "500"},
    }
    for (path, method), statuses in expected.items():
        operation = specification["paths"][path][method]
        if not statuses.issubset(operation["responses"]):
            raise AssertionError(f"{method.upper()} {path}: documented responses are incomplete")
        if {"bearerAuth": []} not in operation.get("security", []):
            raise AssertionError(f"{method.upper()} {path}: bearer authentication must be documented")
        for status in statuses - {"200", "201"}:
            content = operation["responses"][status].get("content", {})
            problem_schema = content.get("application/problem+json", {}).get("schema", {})
            if problem_schema.get("$ref") != "#/components/schemas/ApiErrorResponseDTO":
                raise AssertionError(f"{method.upper()} {path}: {status} must document the standard problem response")


def validate_appointment_required_fields(specification: dict, responses: dict) -> None:
    """Validate appointment responses captured from real, authenticated HTTP requests."""
    samples = {
        "AppointmentResponseDTO": [responses[filename] for filename in (
            "appointment-created.json", "appointment-replay.json", "appointment-cancelled.json",
            "appointment-no-show.json", "appointment-rescheduled.json", "appointment-rescheduling-replay.json",
        )],
        "AppointmentEventResponseDTO": (responses["appointment-event-page.json"]["items"]
                                        + responses["appointment-administrative-event-page.json"]["items"]),
        "AppointmentPageResponseDTO": [responses["appointment-page.json"]],
        "AppointmentEventPageResponseDTO": [responses["appointment-event-page.json"],
                                            responses["appointment-administrative-event-page.json"]],
        "OwnerEmailResponseDTO": [responses["appointment-owner-email.json"]],
    }
    for schema_name, required_fields in APPOINTMENT_REQUIRED_FIELDS.items():
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
            validate_enum_fields(validator, schema_name, response, required_fields)
            if validator.is_valid({**response, "unexpectedField": "unexpected"}):
                raise AssertionError(f"{schema_name}: unknown response fields must be rejected")
            for field in sorted(required_fields):
                incomplete_response = {key: value for key, value in response.items() if key != field}
                if validator.is_valid(incomplete_response):
                    raise AssertionError(f"{schema_name}: omission of {field} must be rejected")
            for field, value in response.items():
                if type(value) is int:
                    for invalid_value in (str(value), value + 0.5):
                        if validator.is_valid({**response, field: invalid_value}):
                            raise AssertionError(f"{schema_name}: {field} must reject non-integer values")
            for field, property_schema in schema["properties"].items():
                nullable = "null" in property_schema.get("type", [])
                if validator.is_valid({**response, field: None}) != nullable:
                    raise AssertionError(f"{schema_name}: {field} nullable contract is inconsistent")
                if "enum" in property_schema and validator.is_valid({**response, field: "INVALID_ENUM_VALUE"}):
                    raise AssertionError(f"{schema_name}: {field} must reject values outside its enum")


def validate_administrative_appointment_contract(specification: dict, responses: dict) -> None:
    """Check the new administrative request and real HTTP transition/replay responses."""
    if specification["info"]["version"] != "0.8.0":
        raise AssertionError("Administrative appointment changes require application contract 0.8.0")
    request_fields = {
        "clientRequestId", "availabilitySlotId", "expectedAvailabilitySlotVersion", "expectedVersion",
        "reason", "agreementChannel", "agreementContactedAt", "agreementNote",
    }
    request_schema = specification["components"]["schemas"]["RescheduleAppointmentRequestDTO"]
    if set(request_schema.get("required", [])) != request_fields:
        raise AssertionError("RescheduleAppointmentRequestDTO: every field must be required")
    request_validator = response_validator(specification, "RescheduleAppointmentRequestDTO")
    request = {
        "clientRequestId": "0a9b3b71-42f6-4a98-a834-c84e64f40475",
        "availabilitySlotId": "7fd7a36c-1dc0-45be-b1e0-9198e13a1940",
        "expectedAvailabilitySlotVersion": 0,
        "expectedVersion": 1,
        "reason": "Cambio acordado con el propietario",
        "agreementChannel": "PHONE",
        "agreementContactedAt": "2026-10-08T10:00:00-05:00",
        "agreementNote": "El propietario acepta el nuevo horario.",
    }
    request_validator.validate(request)
    if request_validator.is_valid({**request, "unexpectedField": "unexpected"}):
        raise AssertionError("RescheduleAppointmentRequestDTO: unknown fields must be rejected")
    for field in request_fields:
        if request_validator.is_valid({key: value for key, value in request.items() if key != field}):
            raise AssertionError(f"RescheduleAppointmentRequestDTO: omission of {field} must be rejected")
        if request_validator.is_valid({**request, field: None}):
            raise AssertionError(f"RescheduleAppointmentRequestDTO: {field} must reject null")
    for field in ("expectedVersion", "expectedAvailabilitySlotVersion"):
        for invalid_value in (-1, "1", 1.5, True):
            if request_validator.is_valid({**request, field: invalid_value}):
                raise AssertionError(f"RescheduleAppointmentRequestDTO: {field} accepts an invalid version")
    for field in ("reason", "agreementNote"):
        maximum_length = 1000 if field == "reason" else 500
        for invalid_value in ("", "x" * (maximum_length + 1), 123):
            if request_validator.is_valid({**request, field: invalid_value}):
                raise AssertionError(f"RescheduleAppointmentRequestDTO: {field} accepts invalid text")
    for invalid_value in ("INVALID", "", 123):
        if request_validator.is_valid({**request, "agreementChannel": invalid_value}):
            raise AssertionError("RescheduleAppointmentRequestDTO: invalid agreement channel accepted")
    for invalid_value in (1791471600, "2026-10-08T10:00:00"):
        if request_validator.is_valid({**request, "agreementContactedAt": invalid_value}):
            raise AssertionError("RescheduleAppointmentRequestDTO: contact time requires text with offset")

    event_schema = specification["components"]["schemas"]["AppointmentEventResponseDTO"]
    event_types = set(event_schema["properties"]["eventType"].get("enum", []))
    if not {"CANCELLED", "NO_SHOW", "RESCHEDULED", "DAILY_CUTOFF"}.issubset(event_types):
        raise AssertionError("Appointment events must distinguish human changes from daily cutoff")
    for field in ("previousStatus", "newStatus"):
        if "NO_SHOW" not in event_schema["properties"][field].get("enum", []):
            raise AssertionError(f"AppointmentEventResponseDTO: {field} must document NO_SHOW")
    expected_states = {"appointment-cancelled.json": "CANCELLED", "appointment-no-show.json": "NO_SHOW"}
    for filename, expected_state in expected_states.items():
        if responses[filename]["status"] != expected_state:
            raise AssertionError(f"{filename}: expected a real {expected_state} response")
    if responses["appointment-rescheduled.json"] != responses["appointment-rescheduling-replay.json"]:
        raise AssertionError("Reprogramming replay must preserve the response and appointment version")
    if not responses["appointment-administrative-event-page.json"]["items"]:
        raise AssertionError("Administrative appointment history requires real HTTP events")


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
        "appointment-created.json": "AppointmentResponseDTO",
        "appointment-replay.json": "AppointmentResponseDTO",
        "appointment-conflict-error.json": "ApiErrorResponseDTO",
        "appointment-page.json": "AppointmentPageResponseDTO",
        "appointment-event-page.json": "AppointmentEventPageResponseDTO",
        "appointment-owner-email.json": "OwnerEmailResponseDTO",
        "appointment-cancelled.json": "AppointmentResponseDTO",
        "appointment-no-show.json": "AppointmentResponseDTO",
        "appointment-rescheduled.json": "AppointmentResponseDTO",
        "appointment-rescheduling-replay.json": "AppointmentResponseDTO",
        "appointment-rescheduling-conflict-error.json": "ApiErrorResponseDTO",
        "appointment-administrative-event-page.json": "AppointmentEventPageResponseDTO",
    }
    responses = {}
    for filename, schema_name in examples.items():
        response = json.loads((args.examples / filename).read_text(encoding="utf-8"))
        response_validator(specification, schema_name).validate(response)
        responses[filename] = response
    validate_availability_required_fields(specification, responses)
    validate_appointment_required_fields(specification, responses)
    validate_appointment_operation_responses(specification)
    validate_administrative_appointment_contract(specification, responses)
    print(f"OpenAPI {specification['openapi']} valid; {len(examples)} HTTP response examples match their schemas; "
          f"required-field, integer and enum rejection checks passed for {len(AVAILABILITY_REQUIRED_FIELDS)} availability and "
          f"{len(APPOINTMENT_REQUIRED_FIELDS)} appointment schemas; appointment nullability, operation responses and "
          "administrative rescheduling request/replay checked.")


if __name__ == "__main__":
    main()
