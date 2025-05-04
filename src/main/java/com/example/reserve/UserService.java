package com.example.reserve;

import com.example.reserve.exception.ErrorCode;
import com.example.reserve.exception.ReserveException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import java.time.Instant;

@RequiredArgsConstructor
@Service
public class UserService {

    // Spring WebFlux 환경에서 비동기/논블로킹 방식으로 Redis에 접근
    private final ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    // 대기열 등록
    public Mono<Long> registerWaitQueue(final Long userId){

        long enterTimestamp = Instant.now().getEpochSecond();

        return reactiveRedisTemplate.opsForZSet().add("user-que", userId.toString(), enterTimestamp)
                .filter(i -> i)
                .switchIfEmpty(Mono.error(new ReserveException(HttpStatus.BAD_REQUEST, ErrorCode.ALREADY_REGISTERED_USER)))
                .flatMap(i -> reactiveRedisTemplate.opsForZSet().rank("user-queue", userId.toString()))
                .map(i -> i >= 0 ? i+1 : i);
    }

    // 사용자의 랭킹 조회
    public Mono<String> searchUserRanking(Long userId) {
        return reactiveRedisTemplate.opsForZSet().rank("user-que", userId.toString())

                // 대기큐 안에 해당되는 유저가 없으면 예외 발생
                .switchIfEmpty(Mono.error(new ReserveException(HttpStatus.BAD_REQUEST, ErrorCode.USER_NOT_FOUND_IN_THE_QUEUE)))
                .flatMap(rank -> Mono.just(userId.toString() + "님의 순위 : " + (rank + 1) + "번째"));
    }
}
