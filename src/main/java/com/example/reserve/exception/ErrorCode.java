package com.example.reserve.exception;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    UNKNOWN("UNKNOWN", "알 수 없는 에러가 발생했습니다."),

    ALREADY_REGISTERED_USER("ALREADY_REGISTERED_USER", "이미 등록된 유저입니다."),

    USER_NOT_FOUND_IN_THE_QUEUE("USER_NOT_FOUND_IN_THE_QUEUE", "해당 유저는 대기열에 참가되어있지 않습니다");

    private final String errorCode;

    private final String message;
}
