package edu.uniquindio.grownupsvet.grownupsvet_backend.staff.exception;

import org.springframework.http.HttpStatus;

public class StaffOperationException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;
    private final String detail;

    public StaffOperationException(HttpStatus status, String errorCode, String detail) {
        super(detail);
        this.status = status;
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public HttpStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public String getDetail() { return detail; }
}
