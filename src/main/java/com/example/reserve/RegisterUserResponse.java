package com.example.reserve;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RegisterUserResponse {
    private Long position;
    private String queueType;

    public RegisterUserResponse(Long position, String queueType) {
        this.position = position;
        this.queueType = queueType;
    }
}
