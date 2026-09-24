package edu.uniquindio.grownupsvet.grownupsvet_backend.availability.exception;

import org.springframework.http.HttpStatus;
import org.springframework.util.Assert;

public class AvailabilityOperationException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    public AvailabilityOperationException(HttpStatus status, String errorCode, String detail) {
        super(detail);
        Assert.isTrue(status.isError(), "status must represent an error");
        Assert.hasText(errorCode, "errorCode must not be blank");
        this.status = status;
        this.errorCode = errorCode;
    }

    public HttpStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
}
