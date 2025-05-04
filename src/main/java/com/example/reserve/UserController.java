package com.example.reserve;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RequiredArgsConstructor
@RequestMapping("/user")
@RestController
public class UserController {

    private final UserService userService;

    // 대기열 등록
    @PostMapping("/enter")
    public Mono<?> registerUser(@RequestParam(name = "user_id") Long userId){
        return userService.registerWaitQueue(userId).map(RegisterUserResponse::new);
    }

    // 사용자 랭킹 조회
    @GetMapping("/search/ranking")
    public Mono<?> searchUserRanking(@RequestParam(name = "user_id") Long userId) {
        return userService.searchUserRanking(userId);
    }
}
