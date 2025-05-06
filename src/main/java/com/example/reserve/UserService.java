package com.example.reserve;

import com.example.reserve.exception.ErrorCode;
import com.example.reserve.exception.ReserveException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.Instant;

@Slf4j
@RequiredArgsConstructor
@Service
public class UserService {

    // Spring WebFlux 환경에서 비동기/논블로킹 방식으로 Redis에 접근
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;
    private final ApplicationEventPublisher eventPublisher;

    public static final String WAIT_QUEUE = ":user-queue:wait";
    public static final String ALLOW_QUEUE = ":user-queue:allow";

    /**
    * 대기열 등록
    * */
    public Mono<Long> registerWaitQueue(Long userId, String queueType){

        long enterTimestamp = Instant.now().getEpochSecond();

        return reactiveRedisTemplate.opsForZSet().add(queueType + WAIT_QUEUE, userId.toString(), enterTimestamp)
                .filter(i -> i)
                .switchIfEmpty(Mono.error(new ReserveException(HttpStatus.BAD_REQUEST, ErrorCode.ALREADY_REGISTERED_USER)))
                .flatMap(i -> {
                    eventPublisher.publishEvent(new QueueUpdateEvent(queueType));
                    return reactiveRedisTemplate.opsForZSet().rank(queueType + WAIT_QUEUE, userId.toString());
                })
                .map(i -> i >= 0 ? i+1 : i)
                .doOnSuccess(result -> log.info("{}님 {}번째로 사용자 대기열 등록 성공", userId, result));
    }

    /**
    * 사용자의 순위 조회
    * */
    public Mono<Long> searchUserRanking(Long userId, String queueType) {
        return reactiveRedisTemplate.opsForZSet().rank(queueType + WAIT_QUEUE, userId.toString())
                // 대기큐 안에 해당되는 유저가 없으면 예외 발생
                .switchIfEmpty(Mono.error(new ReserveException(HttpStatus.BAD_REQUEST, ErrorCode.USER_NOT_FOUND_IN_THE_QUEUE)))
                .flatMap(rank -> Mono.just((rank + 1)))
                .doOnSuccess(result -> log.info("{}님의 순위 : {}번째", userId, result));
    }

    /**
    * 대기큐에 있는 상위 count 명을 허용큐로 옮김
    * */
    public Mono<Long> allowUser(String queueType, Long count) {

        // 만약 대기 큐의 인원이 count 명보다 적더라도 ZSet.popMin은 내부적으로 count보다 적은 요소가 있으면 있는 만큼만 반환하므로 따로 처리 필요 없음
        return reactiveRedisTemplate.opsForZSet().popMin(queueType + WAIT_QUEUE, count)
                .flatMap(member -> reactiveRedisTemplate.opsForZSet().add(queueType + ALLOW_QUEUE, member.getValue(), Instant.now().getEpochSecond()))
                .count()
                .doOnSuccess(allowedCount -> log.info("허용큐로 이동된 사용자 수: {}", allowedCount));
    }

    /**
     * 허용큐 내부에서 특정 사용자가 입장 가능한지 여부 파악
    * */
    public Mono<Boolean> isAllowedUser(String queueType, Long userId) {

        // 사용자가 허용 큐에 있다면 순위 반환 ( 0부터 ~ )
        return reactiveRedisTemplate.opsForZSet().rank(queueType + ALLOW_QUEUE, userId.toString())
                .defaultIfEmpty( -1L) // 사용자가 허용 큐에 없으면 -1로 대체
                .map(rank -> rank >= 0);
    }

    /**
     * 대기큐 등록 삭제
    * */
    public Mono<Void> cancelUser(Long userId, String queueType) {
        return reactiveRedisTemplate.opsForZSet().remove(queueType + WAIT_QUEUE, userId.toString())
                .flatMap(removedCount -> {
                    if (removedCount == 0) {
                        return Mono.error(new ReserveException(HttpStatus.BAD_REQUEST, ErrorCode.USER_NOT_FOUND_IN_THE_QUEUE));
                    }
                    eventPublisher.publishEvent(new QueueUpdateEvent(queueType));
                    return Mono.<Void>empty();
                })
                .doOnSuccess(v -> log.info("{}님 대기열에서 취소 완료", userId));
    }
}
