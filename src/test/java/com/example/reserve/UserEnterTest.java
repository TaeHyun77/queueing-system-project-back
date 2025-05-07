package com.example.reserve;

import com.example.reserve.exception.ErrorCode;
import com.example.reserve.exception.ReserveException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.core.ReactiveRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import reactor.test.StepVerifier;
import java.time.Instant;
import java.util.Objects;
import static com.example.reserve.UserService.ALLOW_QUEUE;
import static com.example.reserve.UserService.WAIT_QUEUE;

@ActiveProfiles("test")
@Import({TestRedisConfiguration.class})
@SpringBootTest
public class UserEnterTest {

    @Autowired
    private UserService userService;

    @Autowired
    private ReactiveRedisTemplate<String, String> reactiveRedisTemplate;

    // 실행 전 redis 데이터 초기화
    @BeforeEach
    public void beforeEach() {
        ReactiveRedisConnection reactiveConnection = reactiveRedisTemplate.getConnectionFactory().getReactiveConnection();
        reactiveConnection.serverCommands().flushAll().subscribe();
    }

    @Test
    void registerWaitQueue_등록테스트() {
        StepVerifier.create(userService.registerWaitQueue(1L, "reserve"))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(userService.registerWaitQueue(2L, "reserve"))
                .expectNext(2L)
                .verifyComplete();

        StepVerifier.create(userService.registerWaitQueue(3L, "reserve"))
                .expectNext(3L)
                .verifyComplete();

        // 예외 발생 테스트
        StepVerifier.create(userService.registerWaitQueue(1L, "reserve"))
                .expectError(ReserveException.class)
                .verify();
    }

    @Test
    void registerAndCheck_등록_및_조회테스트() {
        StepVerifier.create(userService.registerWaitQueue(1L, "reserve"))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(userService.registerWaitQueue(2L, "reserve"))
                .expectNext(2L)
                .verifyComplete();

        StepVerifier.create(userService.registerWaitQueue(3L, "reserve"))
                .expectNext(3L)
                .verifyComplete();

        // 조회 테스트
        StepVerifier.create(userService.searchUserRanking(1L, "reserve"))
                .expectNext(1L)
                .verifyComplete();

        // 예외 발생 테스트
        StepVerifier.create(userService.searchUserRanking(100L, "reserve"))
                .expectErrorMatches(throwable ->
                        throwable instanceof ReserveException &&
                                ((ReserveException) throwable).getErrorCode() == ErrorCode.USER_NOT_FOUND_IN_THE_QUEUE
                )
                .verify();
    }

    @Test
    void allowUser_이동_테스트() {
        String queueType = "reserve";

        // 대기큐 등록
        StepVerifier.create(userService.registerWaitQueue(1L, queueType)).expectNext(1L).verifyComplete();
        StepVerifier.create(userService.registerWaitQueue(2L, queueType)).expectNext(2L).verifyComplete();
        StepVerifier.create(userService.registerWaitQueue(3L, queueType)).expectNext(3L).verifyComplete();

        // 허용큐로 2명 이동
        StepVerifier.create(userService.allowUser(queueType, 2L))
                .expectNext(2L)
                .verifyComplete();

        // 허용큐에 1번, 2번 존재 확인
        StepVerifier.create(
                reactiveRedisTemplate.opsForZSet().rank(queueType + ALLOW_QUEUE, "1")
        ).expectNextMatches(Objects::nonNull).verifyComplete();

        StepVerifier.create(
                reactiveRedisTemplate.opsForZSet().rank(queueType + ALLOW_QUEUE, "2")
        ).expectNextMatches(Objects::nonNull).verifyComplete();

        // 대기큐에 남은 인원 1명 확인 (3번)
        StepVerifier.create(reactiveRedisTemplate.opsForZSet().size(queueType + WAIT_QUEUE))
                .expectNext(1L).verifyComplete();
    }

    @Test
    void testIsAllowedUser() {
        String queueType = "reserve";
        Long userId = 1L;

        // 허용 큐에 사용자 등록
        reactiveRedisTemplate.opsForZSet()
                .add(queueType + ALLOW_QUEUE, userId.toString(), Instant.now().getEpochSecond())
                .block();

        StepVerifier.create(userService.isAllowedUser(queueType, userId))
                .expectNext(true)
                .verifyComplete();

        // 존재하지 않는 사용자에 대한 확인
        StepVerifier.create(userService.isAllowedUser(queueType, 999L))
                .expectNext(false)
                .verifyComplete();
    }


}
