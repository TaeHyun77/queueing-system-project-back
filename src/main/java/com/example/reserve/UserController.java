package com.example.reserve;

import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/user")
@RestController
public class UserController {

    private final UserService userService;
    private final Sinks.Many<String> sink = Sinks.many().multicast().onBackpressureBuffer();

    // 대기열 등록
    @PostMapping("/enter")
    public Mono<?> registerUser(@RequestParam(name = "user_id") String userId,
                                @RequestParam(name = "queueType", defaultValue = "reserve") String queueType){

        long enterTimestamp = Instant.now().getEpochSecond();

        return userService.registerUserToWaitQueue(userId, queueType, enterTimestamp);
    }

    // 새로고침 시 대기열 재등록
    @PostMapping("/reEnter")
    public Mono<Void> reEnterQueue(@RequestParam(name = "user_id") String user_id,
                                   @RequestParam(name = "queueType", defaultValue = "reserve") String queueType) {

        log.info("reEnter 호출 완료");
        return userService.reEnterWaitQueue(user_id, queueType);
    }

    // 대기열에 사용자 존재 유무 확인
    @GetMapping("/isExist")
    public Mono<Boolean> isExistUserInQueue(@RequestParam(name = "user_id") String userId,
                                            @RequestParam(name = "queueType", defaultValue = "reserve") String queueType) {
        return userService.isExistUserInQueue(userId, queueType);
    }

    // 대기열 취소
    @DeleteMapping("/cancel")
    public Mono<Void> cancelUser(@RequestParam(name = "user_id") String userId,
                                                 @RequestParam(name = "queueType", defaultValue = "reserve") String queueType) {
        return userService.cancelWaitUser(userId, queueType);
    }

    // 허용큐 삭제
    @DeleteMapping("/remove/allow")
    public Mono<Void> removeAllowUser(@RequestParam(name = "user_id") String userId,
                                 @RequestParam(name = "queueType", defaultValue = "reserve") String queueType) {
        return userService.removeAllowUser(userId, queueType);
    }

    // 사용자 랭킹 조회
    @GetMapping("/search/ranking")
    public Mono<Long> searchUserRanking(@RequestParam(name = "user_id") String userId,
                                     @RequestParam(name = "queueType", defaultValue = "reserve") String queueType) {
        return userService.searchUserRanking(userId, queueType);
    }

    // 허용 큐 이동
    @PostMapping("/allow")
    public Mono<?> allowUser(@RequestParam(name = "queueType", defaultValue = "reserve") String queueType,
                             @RequestParam(name = "count") Long count) {
        return userService.allowUser(queueType, count);
    }

    // 진입 여부 확인
    @GetMapping("/isAllowed")
    public Mono<Boolean> isAllowedUser(@RequestParam(name = "user_id") String userId,
                                       @RequestParam(name = "queueType", defaultValue = "reserve") String queueType) {
        return userService.isAllowedUser(userId, queueType);
    }

    // 토큰 유효성 확인
    @GetMapping("/isValidateToken")
    public Mono<Boolean> isAccessTokenValid(@RequestParam(name = "user_id") String userId,
                                            @RequestParam(name = "queueType", defaultValue = "reserve") String queueType,
                                            @RequestParam(name = "token") String token) {
        return userService.isAccessTokenValid(userId, queueType, token);
    }

    // 쿠키 토큰 저장
    @GetMapping("/createCookie")
    public Mono<ResponseEntity<String>> sendCookie(@RequestParam(name = "user_id") String userId,
                                              @RequestParam(defaultValue = "reserve") String queueType,
                                              HttpServletResponse response) {
        return userService.sendCookie(userId, queueType, response);
    }
}
