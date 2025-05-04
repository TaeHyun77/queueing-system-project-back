package com.example.reserve.exception;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

@ControllerAdvice
public class CustomExceptionHandler {

    @ExceptionHandler(ReserveException.class)
    protected ResponseEntity<ErrorDto> handleCustom400Exception(ReserveException ex) {
        return ErrorDto.toResponseEntity(ex);
    }

}