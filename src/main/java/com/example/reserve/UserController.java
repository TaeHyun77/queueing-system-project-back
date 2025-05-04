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
    public Mono<?> registerUser(@RequestParam(name = "user_id") Long userId,
                                @RequestParam(name = "queueType", defaultValue = "reserve") String queueType){
        return userService.registerWaitQueue(userId, queueType);
    }

    // 사용자 랭킹 조회
    @GetMapping("/search/ranking")
    public Mono<?> searchUserRanking(@RequestParam(name = "user_id") Long userId,
                                     @RequestParam(name = "queueType", defaultValue = "reserve") String queueType) {
        return userService.searchUserRanking(userId, queueType);
    }

    // 허용 쿠
    @PostMapping("/allow")
    public Mono<?> allowUser(@RequestParam(name = "queueType", defaultValue = "reserve") String queueType,
                             @RequestParam(name = "count") Long count) {
        return userService.allowUser(queueType, count);
    }
}
