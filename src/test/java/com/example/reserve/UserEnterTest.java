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
    void 대기열_사용자_등록_테스트() {

        StepVerifier.create(userService.registerUserToWaitQueue("1번", "reserve", Instant.now().getEpochSecond()))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(userService.registerUserToWaitQueue("2번", "reserve", Instant.now().getEpochSecond()))
                .expectNext(2L)
                .verifyComplete();

        StepVerifier.create(userService.registerUserToWaitQueue("3번", "reserve", Instant.now().getEpochSecond()))
                .expectNext(3L)
                .verifyComplete();

        // 예외 발생 테스트
        StepVerifier.create(userService.registerUserToWaitQueue("1번", "reserve", Instant.now().getEpochSecond()))
                .expectError(ReserveException.class)
                .verify();
    }

    @Test
    void 대기열_사용자_조회_테스트() {

        StepVerifier.create(userService.registerUserToWaitQueue("1번", "reserve", Instant.now().getEpochSecond()))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(userService.registerUserToWaitQueue("2번", "reserve", Instant.now().getEpochSecond()))
                .expectNext(2L)
                .verifyComplete();

        StepVerifier.create(userService.registerUserToWaitQueue("3번", "reserve", Instant.now().getEpochSecond()))
                .expectNext(3L)
                .verifyComplete();

        // 조회 테스트
        StepVerifier.create(userService.searchUserRanking("1번", "reserve", "wait"))
                .expectNext(1L)
                .verifyComplete();

        // 예외 발생 테스트
        StepVerifier.create(userService.searchUserRanking("조회예외발생테스트", "reserve", "wait"))
                .expectErrorMatches(throwable ->
                        throwable instanceof ReserveException &&
                                ((ReserveException) throwable).getErrorCode() == ErrorCode.USER_NOT_FOUND_IN_THE_QUEUE
                )
                .verify();
    }

    @Test
    void 대기열_사용자_삭제_테스트() {

        StepVerifier.create(userService.registerUserToWaitQueue("1번", "reserve", Instant.now().getEpochSecond()))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(userService.registerUserToWaitQueue("2번", "reserve", Instant.now().getEpochSecond()))
                .expectNext(2L)
                .verifyComplete();

        StepVerifier.create(userService.registerUserToWaitQueue("3번", "reserve", Instant.now().getEpochSecond()))
                .expectNext(3L)
                .verifyComplete();

        StepVerifier.create(userService.cancelWaitUser("1번", "reserve", "wait"))
                .verifyComplete();

        StepVerifier.create(reactiveRedisTemplate.opsForZSet().size("reserve" + WAIT_QUEUE))
                .expectNext(2L).verifyComplete();
    }

    @Test
    void 참가열_이동_및_참가열_사용자_삭제_테스트() {
        String queueType = "reserve";

        // 대기열 등록
        StepVerifier.create(userService.registerUserToWaitQueue("1번", "reserve", Instant.now().getEpochSecond()))
                .expectNext(1L)
                .verifyComplete();

        StepVerifier.create(userService.registerUserToWaitQueue("2번", "reserve", Instant.now().getEpochSecond()))
                .expectNext(2L)
                .verifyComplete();

        StepVerifier.create(userService.registerUserToWaitQueue("3번", "reserve", Instant.now().getEpochSecond()))
                .expectNext(3L)
                .verifyComplete();

        // 참가열로 2명 이동
        StepVerifier.create(userService.allowUser(queueType, 2L))
                .expectNext(2L)
                .verifyComplete();

        // 참가열에 1번, 2번 존재 확인
        StepVerifier.create(
                reactiveRedisTemplate.opsForZSet().rank(queueType + ALLOW_QUEUE, "1번")
        ).expectNextMatches(Objects::nonNull).verifyComplete();

        StepVerifier.create(
                reactiveRedisTemplate.opsForZSet().rank(queueType + ALLOW_QUEUE, "2번")
        ).expectNextMatches(Objects::nonNull).verifyComplete();

        // 대기열에 남은 인원 1명 확인 (3번)
        StepVerifier.create(reactiveRedisTemplate.opsForZSet().size(queueType + WAIT_QUEUE))
                .expectNext(1L).verifyComplete();

        // 참가열에서 1번 삭제
        StepVerifier.create(userService.cancelWaitUser("1번", "reserve", "allow"))
                .verifyComplete();

        // 참가열에 남은 인원 1명 확인
        StepVerifier.create(reactiveRedisTemplate.opsForZSet().size(queueType + ALLOW_QUEUE))
                .expectNext(1L).verifyComplete();
    }
}
