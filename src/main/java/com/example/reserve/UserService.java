package com.example.reserve;

import com.example.reserve.exception.ErrorCode;
import com.example.reserve.exception.ReserveException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService {

    // Spring WebFlux 환경에서 비동기/논블로킹 방식으로 Redis에 접근
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public static final String WAIT_QUEUE = ":user-queue:wait";
    public static final String ALLOW_QUEUE = ":user-queue:allow";
    public static final String ACCESS_TOKEN = ":user-access:";

    /**
    * 대기열 등록
    * */
    public Mono<Long> registerUser(String userId, String queueType){

        long enterTimestamp = Instant.now().getEpochSecond();

        return reactiveRedisTemplate.opsForZSet().add(queueType + WAIT_QUEUE, userId, enterTimestamp)
                .filter(i -> i)
                .switchIfEmpty(Mono.error(new ReserveException(HttpStatus.BAD_REQUEST, ErrorCode.ALREADY_REGISTERED_USER)))
                .flatMap(i -> {
                    eventPublisher.publishEvent(new QueueUpdateEvent(queueType));
                    return reactiveRedisTemplate.opsForZSet().rank(queueType + WAIT_QUEUE, userId);
                })
                .map(i -> i >= 0 ? i+1 : i)
                .doOnSuccess(result -> log.info("{}님 {}번째로 사용자 대기열 등록 성공", userId, result));
    }

    /**
    * 사용자의 순위 조회
    * */
    public Mono<Long> searchUserRanking(String userId, String queueType) {

        return isAllowedUser(queueType, userId)
                .flatMap(allowed -> {
                    if (allowed) {
                        // 허용된 유저는 프론트에서 따로 처리
                        return Mono.just(-1L);
                    }

                    return reactiveRedisTemplate.opsForZSet().rank(queueType + WAIT_QUEUE, userId)
                            .switchIfEmpty(Mono.error(new ReserveException(HttpStatus.BAD_REQUEST, ErrorCode.USER_NOT_FOUND_IN_THE_QUEUE)))
                            .map(rank -> rank + 1);
                })
                .doOnSuccess(result -> {
                    if (result == -1L) {
                        log.info("{}님은 허용큐에 있어 순위 조회 불필요", userId);
                    } else {
                        log.info("{}님의 순위 : {}번째", userId, result);
                    }
                });
    }

    /**
    * 대기큐에 있는 상위 count 명을 허용큐로 옮기고, 토큰을 생성하여 redis에 저장 ( 유효 기간 10분 )
    * */
    public Mono<Long> allowUser(String queueType, Long count) {
        return reactiveRedisTemplate.opsForZSet().popMin(queueType + WAIT_QUEUE, count)
                .flatMap(member -> {
                    String userId = member.getValue();

                    return reactiveRedisTemplate.opsForZSet()
                            .add(queueType + ALLOW_QUEUE, member.getValue(), Instant.now().getEpochSecond())
                            .thenReturn(userId);
                })
                .count()
                .doOnSuccess(allowedCount -> log.info("허용큐로 이동된 사용자 수: {}", allowedCount));
    }


    /**
     * 허용큐 내부에서 특정 사용자가 입장 가능한지 여부 파악
    * */
    public Mono<Boolean> isAllowedUser(String userId, String queueType) {

        // 사용자가 허용 큐에 있다면 순위 반환 ( 0부터 ~ )
        return reactiveRedisTemplate.opsForZSet().rank(queueType + ALLOW_QUEUE, userId)
                .defaultIfEmpty( -1L) // 사용자가 허용 큐에 없으면 -1로 대체
                .map(rank -> rank >= 0);
    }

    /**
     * 대기큐 등록 삭제
    * */
    public Mono<Void> cancelUser(String userId, String queueType) {
        return reactiveRedisTemplate.opsForZSet().remove(queueType + WAIT_QUEUE, userId)
                .flatMap(removedCount -> {
                    if (removedCount == 0) {
                        return Mono.error(new ReserveException(HttpStatus.BAD_REQUEST, ErrorCode.USER_NOT_FOUND_IN_THE_QUEUE));
                    }
                    eventPublisher.publishEvent(new QueueUpdateEvent(queueType));
                    return Mono.<Void>empty();
                })
                .doOnSuccess(v -> log.info("{}님 대기열에서 취소 완료", userId));
    }

    @Scheduled(fixedDelay = 3000, initialDelay = 20000) // 실행 10초 후부터 3초마다 스케줄링
    public void moveUserToAllowQ() {
        Long maxAllowedUsers = 3L;

        // 여러 종류의 대기 큐가 있다고 가정
        List<String> queueTypes = List.of("reserve"); // 확장 가능하게

        queueTypes.forEach(queueType -> {
            allowUser(queueType, maxAllowedUsers)
                    .doOnSuccess(count -> {
                        if (count > 0) {
                            log.info("Moved {} users to the allow queue for [{}]", count, queueType);
                            eventPublisher.publishEvent(new QueueUpdateEvent(queueType));
                        } else {
                            log.info("No users to move for queue [{}]", queueType);
                        }
                    })
                    .subscribe(); // 반드시 subscribe() 호출해야 비동기 실행됨
        });
    }

    // 토큰의 유효성 검사
    public Mono<Boolean> isAccessTokenValid(String userId, String queueType, String token) {

        return generateAccessToken(queueType, userId)
                .map(storedToken -> storedToken.equals(token))
                .defaultIfEmpty(false);
    }

    // 유효성 검사를 위한 토큰 생성
    public static Mono<String> generateAccessToken(String userId, String queueType) {
        try {
            // MessageDigest : 해시 알고리즘 사용을 위한 클래스
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String raw = queueType + ACCESS_TOKEN + userId;
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));

            StringBuilder hex = new StringBuilder();
            for (byte b : hash) hex.append(String.format("%02x", b));

            return Mono.just(hex.toString());
        } catch (NoSuchAlgorithmException e) {
            return Mono.error(new RuntimeException("Token 생성 실패", e));
        }
    }

    public Mono<ResponseEntity<String>> sendCookie(String userId, String queueType, HttpServletResponse response) {

        String encodedName = URLEncoder.encode(userId, StandardCharsets.UTF_8);

        return UserService.generateAccessToken(queueType, userId)
                .map(token -> {
                    Cookie cookie = new Cookie(queueType + "_user-access-cookie_" + encodedName, token);
                    cookie.setPath("/");
                    cookie.setMaxAge(300);
                    response.addCookie(cookie);
                    return ResponseEntity.ok("쿠키 발급 완료");
                });
    }
}
