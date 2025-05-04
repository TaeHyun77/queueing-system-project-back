package com.example.reserve.exception;

import lombok.Builder;
import lombok.Getter;
import org.springframework.http.ResponseEntity;

@Builder
@Getter
public class ErrorDto {

    private String code;
    private String msg;
    private String reason;

    public static ResponseEntity<ErrorDto> toResponseEntity(ReserveException ex) {
        ErrorCode errorType = ex.getErrorCode();
        String reason = ex.getReason();

        return ResponseEntity
                .status(ex.getStatus())
                .body(ErrorDto.builder()
                        .code(errorType.getErrorCode())
                        .msg(errorType.getMessage())
                        .reason(reason)
                        .build());
    }
}
