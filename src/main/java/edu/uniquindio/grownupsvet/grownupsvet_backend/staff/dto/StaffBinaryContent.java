package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.dto;

/** Internal transport value, never serialized as JSON. */
public record StaffBinaryContent(byte[] content, String contentType) { }
