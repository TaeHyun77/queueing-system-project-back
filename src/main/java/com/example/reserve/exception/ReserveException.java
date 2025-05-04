
package com.example.reserve.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public class ReserveException extends RuntimeException{

    private final HttpStatus status;
    private final ErrorCode errorCode;
    private final String reason;

    public ReserveException(HttpStatus status, ErrorCode errorCode, String reason) {
        this.status = status;
        this.errorCode = errorCode;
        this.reason = reason;
    }

    public ReserveException(HttpStatus status, ErrorCode errorCode) {
        this.status = status;
        this.errorCode = errorCode;
        this.reason = "";
    }
}
