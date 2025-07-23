package com.example.reserve;

import com.example.reserve.exception.ErrorCode;
import com.example.reserve.exception.ReserveException;
import com.example.reserve.kafka.KafkaProducerService;
import com.example.reserve.kafka.QueueMessage;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

@Slf4j
@Getter
@RequiredArgsConstructor
@Service
public class UserService {

    // Spring WebFlux 환경에서 비동기/논블로킹 방식으로 Redis에 접근
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final KafkaProducerService kafkaProducerService;
    private final ObjectMapper objectMapper;
    private final Sinks.Many<QueueEventPayload> sink = Sinks.many().replay().limit(1);

    public static final String WAIT_QUEUE = ":user-queue:wait";
    public static final String ALLOW_QUEUE = ":user-queue:allow";
    public static final String ACCESS_TOKEN = ":user-access:";

    @KafkaListener(topics = "queueing-system", groupId = "queue-event-group")
    public void consume(String message) {
        try {
            QueueMessage queueEvent = objectMapper.readValue(message, QueueMessage.class);

            // SSE로 연결된 클라이언트에 전달
            sink.tryEmitNext(new QueueEventPayload(queueEvent.getQueueType()));

            log.info("{} 이벤트 전달 성공 !", queueEvent.getQueueType());
        } catch (Exception e) {
            log.error("Kafka 메시지 소비 실패", e);
        }
    }

    /**
     * 대기열 등록
     * */
    public Mono<Long> registerUserToWaitQueue(String userId, String queueType, long enterTimestamp) {

        // 대기열에 사용자 존재 여부
        Mono<Boolean> existsInWaitQueue = isExistUserInWaitOrAllow(userId, queueType, "wait");

        // 참가열에 사용자 존재 여부
        Mono<Boolean> existsInAllowQueue = isExistUserInWaitOrAllow(userId, queueType, "allow");

        String sequenceKey = queueType + ":wait:seq";

        // 대기열이나 참가열에 동일한 사용자가 있다면 대기열 등록 x, 중복 등록을 막기 위함
        return Mono.zip(existsInWaitQueue, existsInAllowQueue)
                .flatMap(tuple -> {
                    boolean inWait = tuple.getT1();
                    boolean inAllow = tuple.getT2();

                    if (inWait || inAllow) {
                        return Mono.error(new ReserveException(HttpStatus.BAD_REQUEST, ErrorCode.ALREADY_REGISTERED_USER));
                    }

                    // Redis 시퀀스
                    return reactiveRedisTemplate.opsForValue()
                            .increment(sequenceKey)
                            .flatMap(seq -> {
                                double score = enterTimestamp + (seq / 1000.0); // 소수점으로 충돌 최소화

                                return reactiveRedisTemplate.opsForZSet()
                                        .add(queueType + WAIT_QUEUE, userId, score)
                                        .filter(i -> i)
                                        .switchIfEmpty(Mono.error(new ReserveException(HttpStatus.BAD_REQUEST, ErrorCode.ALREADY_REGISTERED_USER)))
                                        .flatMap(i -> {
                                            kafkaProducerService.sendMessage("queueing-system", queueType);
                                            return reactiveRedisTemplate.opsForZSet().rank(queueType + WAIT_QUEUE, userId);
                                        })
                                        .map(rank -> rank >= 0 ? rank + 1 : rank)
                                        .doOnSuccess(result -> log.info("{}님 {}번째로 사용자 대기열 등록 성공", userId, result));
                            });
                });
    }

    /**
     * 대기열 or 참가열에서 사용자 존재 여부 확인
     */
    public Mono<Boolean> isExistUserInWaitOrAllow(String userId, String queueType, String queueCategory) {

        String keyType = queueCategory.equals("wait") ? WAIT_QUEUE : ALLOW_QUEUE;

        return introduceArtificialDelay() // Redis rank 전에 지연 추가
                .then(reactiveRedisTemplate.opsForZSet()
                        .rank(queueType + keyType, userId))
                .map(rank -> true)
                .defaultIfEmpty(false)
                .doOnSuccess(exists ->
                        log.info("{}님 {} 존재 여부 : {}", userId, queueCategory.equals("wait") ? "대기열" : "참가열", exists));
    }

    /**
     * 대기열 or 참가열에서 사용자 순위 조회
     * */
    public Mono<Long> searchUserRanking(String userId, String queueType, String queueCategory) {

        String keyType = queueCategory.equals("wait") ? WAIT_QUEUE : ALLOW_QUEUE;

        return introduceArtificialDelay() // Redis rank 전에 지연 추가
                .then(reactiveRedisTemplate.opsForZSet()
                        .rank(queueType + keyType, userId))
                .defaultIfEmpty(-1L) // 사용자가 없으면 -1 반환
                .map(rank -> rank + 1) // 사용자 순위는 0부터 시작하므로 +1
                .doOnNext(rank -> {
                    if (rank <= 0) {
                        log.warn("[{}] {}님이 존재하지 않습니다. 순위: {}", queueCategory, userId, rank);
                    } else {
                        log.info("[{}] {}님의 현재 순위는 {}번입니다.", queueCategory, userId, rank);
                    }
                });
    }

    /**
     * 대기열 or 참가열에서 사용자 등록된 사용자 제거
     * */
    public Mono<Void> cancelWaitUser(String userId, String queueType, String queueCategory) {

        log.info("{}에서 삭제된 사용자 : {}", queueCategory, userId);

        if (queueCategory.equals("wait")) {
            return reactiveRedisTemplate.opsForZSet().remove(queueType + WAIT_QUEUE, userId)
                    .flatMap(removedCount -> {
                        if (removedCount == 0) {
                            return Mono.error(new ReserveException(HttpStatus.BAD_REQUEST, ErrorCode.USER_NOT_FOUND_IN_THE_QUEUE));
                        }
                        kafkaProducerService.sendMessage("queueing-system", queueType);
                        return Mono.<Void>empty();
                    })
                    .doOnSuccess(v -> log.info("{}님 대기열에서 취소 완료", userId));
        } else {
            return reactiveRedisTemplate.opsForZSet().remove(queueType + ALLOW_QUEUE, userId)
                    .flatMap(removedCount -> {
                        if (removedCount == 0) {
                            return Mono.error(new ReserveException(HttpStatus.BAD_REQUEST, ErrorCode.USER_NOT_FOUND_IN_THE_QUEUE));
                        }
                        String tokenTtlKey = "token:" + userId + ":TTL";

                        return reactiveRedisTemplate.delete(tokenTtlKey)
                                .doOnSuccess(deleted -> log.info("{}님의 TTL 키 삭제 완료", userId))
                                .doOnError(e -> log.error("{}님의 TTL 키 삭제 중 오류 발생: {}", userId, e.getMessage()))
                                .then();
                    })
                    .doOnSuccess(v -> log.info("{}님 참가열에서 취소 완료", userId))
                    .doOnError(e -> log.error("{}님 참가열 취소 중 오류 발생: {}", userId, e.getMessage()));
        }
    }

    /**
     * 유효성 검사를 위한 토큰 생성
     * */
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

    /**
     * 토큰 유효성 검사
     * */
    public Mono<Boolean> isAccessTokenValid(String userId, String queueType, String token) {
        String tokenKey = "token:" + userId + ":TTL";

        return reactiveRedisTemplate.opsForValue()
                .get(tokenKey) // Redis에서 저장된 사용자의 TTL 정보 가져오기
                .flatMap(storedToken -> {
                    if (storedToken == null) {
                        return Mono.just(false); // TTL 만료됨
                    }

                    // TTL이 만료되지 않았다면
                    return generateAccessToken(userId, queueType)
                            .map(generatedToken -> generatedToken.equals(token));
                })
                .defaultIfEmpty(false); // 키 자체가 아예 없을 경우
    }

    /**
     * 쿠키에 생성한 토큰을 저장
     * */
    public Mono<ResponseEntity<String>> sendCookie(String userId, String queueType, HttpServletResponse response) {

        log.info("userId : {}", userId);
        String encodedName = URLEncoder.encode(userId, StandardCharsets.UTF_8);

        return UserService.generateAccessToken(userId, queueType)
                .map(token -> {
                    Cookie cookie = new Cookie(queueType + "_user-access-cookie_" + encodedName, token);
                    cookie.setPath("/");
                    cookie.setMaxAge(300);
                    response.addCookie(cookie);
                    return ResponseEntity.ok("쿠키 발급 완료");
                });
    }

    /**
     * 새로고침 시 대기열 후순위 재등록 로직
     * */
    public Mono<Void> reEnterWaitQueue(String userId, String queueType) {

        long newTimestamp = Instant.now().toEpochMilli(); // 현재 시각, 새로고침 시 대기열 후순위 설정을 위함

        return reactiveRedisTemplate.opsForZSet()
                .add(queueType + WAIT_QUEUE, userId, newTimestamp)
                .then(Mono.fromRunnable(() ->kafkaProducerService.sendMessage("queueing-system", queueType)))
                .then();
    }

    /**
     * 대기열에 있는 상위 count 명을 참가열로 옮기고, 토큰을 생성하여 redis에 저장 ( 유효 기간 10분 )
     */
    public Mono<Long> allowUser(String queueType, Long count) {

        return reactiveRedisTemplate.opsForZSet()
                .popMin(queueType + WAIT_QUEUE, count)
                .flatMap(member -> {
                    String userId = member.getValue();
                    log.info("참가열 이동 사용자 : {}", userId);
                    long timestamp = Instant.now().toEpochMilli();
                    String tokenKey = "token:" + userId + ":TTL";

                    // 참가열 추가 + 토큰 저장 (TTL 10분)
                    return reactiveRedisTemplate.opsForZSet()
                            .add(queueType + ALLOW_QUEUE, userId, timestamp)
                            .then(
                                    reactiveRedisTemplate.opsForValue()
                                            .set(tokenKey, "allowed", Duration.ofMinutes(10))
                            )
                            .thenReturn(userId);
                })
                .count()
                .doOnSuccess(allowedCount -> log.info("참가열로 이동된 사용자 수: {}", allowedCount));
    }

    /**
     * 대기열의 사용자를 참가열로 maxAllowedUsers 명 옮기는 scheduling 코드
     * */
    @Scheduled(fixedDelay = 5000, initialDelay = 30000) // 실행 10초 후부터 3초마다 스케줄링
    public void moveUserToAllowQ() {
        Long maxAllowedUsers = 3L;

        // 여러 종류의 대기 큐가 있다고 가정
        List<String> queueTypes = List.of("reserve"); // 추후 확장 가능하게

        queueTypes.forEach(queueType -> {
            allowUser(queueType, maxAllowedUsers)
                    .doOnSuccess(count -> {
                        if (count > 0) {
                            log.info("{} 허용열로 이동한 사용자 : {}", queueType, count);
                            kafkaProducerService.sendMessage("queueing-system", queueType);
                        } else {
                            log.info("{} 허용열로 이동한 사용자 : 0 ", queueType);
                        }
                    })
                    .subscribe(); // Mono, Flux 반환형이 아니므로 직접 호출해줘야 함
        });
    }

    private Mono<Void> introduceArtificialDelay() {
        return Mono.delay(Duration.ofMillis(500)).then();
    }
}
