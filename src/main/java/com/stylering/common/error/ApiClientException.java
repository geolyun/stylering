package com.stylering.common.error;

import org.springframework.http.HttpStatus;

public class ApiClientException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiClientException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }
}
