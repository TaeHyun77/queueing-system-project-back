package com.example.reserve;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class RegisterUserResponse {
    private Long position;

    public RegisterUserResponse(Long position) {
        this.position = position;
    }

}
