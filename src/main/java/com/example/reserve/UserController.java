package com.example.reserve;

import com.example.reserve.kafka.KafkaConsumerService;
import com.example.reserve.kafka.KafkaProducerController;
import com.example.reserve.kafka.KafkaProducerService;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@RequiredArgsConstructor
@RequestMapping("/user")
@RestController
public class UserController {

    private final UserService userService;

    @PostMapping("/register")
    public Mono<?> registerUser(@RequestParam(defaultValue = "reserve") String queueType, @RequestParam String userId) {

        Instant now = Instant.now();
        long enterTimestamp = now.getEpochSecond() * 1_000_000_000L + now.getNano(); // 입장 시간을 나노초로 설정

        log.info("userId : {}, enterTimestamp : {}", userId, enterTimestamp);

        return userService.registerUserToWaitQueue(userId, queueType, enterTimestamp);
    }

    // 대기열 or 참가열에서 사용자 존재 유무 확인
    @GetMapping("/isExist")
    public Mono<Boolean> isExistUserInQueue(@RequestParam(name = "user_id") String userId,
                                            @RequestParam(name = "queueType", defaultValue = "reserve") String queueType,
                                            @RequestParam(name = "queueCategory") String queueCategory) {
        return userService.isExistUserInWaitOrAllow(userId, queueType, queueCategory);
    }

    // 대기열 or 참가열에서 사용자 순위 조회
    @GetMapping("/search/ranking")
    public Mono<Long> searchUserRanking(@RequestParam(name = "user_id") String userId,
                                        @RequestParam(name = "queueType", defaultValue = "reserve") String queueType,
                                        @RequestParam(name = "queueCategory") String queueCategory) {
        return userService.searchUserRanking(userId, queueType, queueCategory);
    }

    // 대기열 or 참가열에서 사용자 제거
    @DeleteMapping("/cancel")
    public Mono<Void> cancelUser(@RequestParam(name = "user_id") String userId,
                                 @RequestParam(name = "queueType", defaultValue = "reserve") String queueType,
                                 @RequestParam(name = "queueCategory") String queueCategory) {
        return userService.cancelWaitUser(userId, queueType, queueCategory);
    }

    // 새로고침 시 대기열 후순위 재등록
    @PostMapping("/reEnter")
    public Mono<Void> reEnterQueue(@RequestParam(name = "user_id") String user_id,
                                   @RequestParam(name = "queueType", defaultValue = "reserve") String queueType) {

        log.info("reEnter 호출 완료");
        return userService.reEnterWaitQueue(user_id, queueType);
    }

    // 대기열 상위 count명을 참가열 이동
    @PostMapping("/allow")
    public Mono<?> allowUser(@RequestParam(name = "queueType", defaultValue = "reserve") String queueType,
                             @RequestParam(name = "count") Long count) {
        return userService.allowUser(queueType, count);
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

