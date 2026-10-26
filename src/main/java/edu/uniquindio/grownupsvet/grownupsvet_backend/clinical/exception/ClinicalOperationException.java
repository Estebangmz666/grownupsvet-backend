package edu.uniquindio.grownupsvet.grownupsvet_backend.clinical.exception;

import org.springframework.http.HttpStatus;

public class ClinicalOperationException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;
    private final String detail;

    public ClinicalOperationException(HttpStatus status, String errorCode, String detail) {
        super(detail);
        this.status = status;
        this.errorCode = errorCode;
        this.detail = detail;
    }

    public HttpStatus getStatus() { return status; }
    public String getErrorCode() { return errorCode; }
    public String getDetail() { return detail; }
}
