package com.example.reserve;

import com.example.reserve.exception.ErrorCode;
import com.example.reserve.exception.ReserveException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

import java.util.Locale;
import java.util.Map;

@Slf4j
@RestController
public class QueueSseController {

    ObjectMapper objectMapper = new ObjectMapper();

    private final UserService userService;

    /*
     * sink : 이벤트를 push 하는 통로, 여기서는 QueueEventPayload 타입의 통로를 만든 것
     * replay() : 마지막으로 발생한 이벤트를 캐싱해 두었다가, 나중에 구독한 사용자에게도 해당 이벤트를 재전달할 수 있는 방식
     * */
    private final Sinks.Many<QueueEventPayload> sink;

    public QueueSseController(UserService userService) {
        this.userService = userService;
        this.sink = userService.getSink();
    }

    /* 1
     * 서버와 클라이언트가 SSE 스트림 연결
     * produces = MediaType.TEXT_EVENT_STREAM_VALUE : text/event-stream 타입의 응답을 반환할 것을 의미
     * ServerSentEvent<String> : SSE 형식의 메시지를 의미, Flux<>로 감쌌으므로 여러 개의 sse 메세지를 실시간으로 전송한다는 뜻
     * */
    @GetMapping(value = "/queue/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamQueue(@RequestParam String userId, @RequestParam String queueType) {
        log.info("sse 연결 요청");

        /* 2
         * 서버에서 발생하는 sink.tryEmitNext()를 받아 처리하는 실시간 이벤트 스트림
         * sink.asFlux() : 서버 내부에서 발생하는 모든 이벤트를 구독, filter를 통해 모든 이벤트 중 클라이언트가 요청한 queueType과 일치하는 이벤트만 골라서 처리
         *
         * [ 사용자가 어느 열에 있는지 확인 ]
         * 사용자가 참가열에 존재한다면 → 'confirmed' 이벤트를 클라이언트로 보내 예약 페이지로 이동하게 함
         * 사용자가 대기열에 존재한다면 → 'update' 이벤트와 사용자의 순위를 반환
         * */
        return sink.asFlux()
                .filter(e -> e.getQueueType().equals(queueType))
                .flatMap(e ->
                        userService.isExistUserInWaitOrAllow(userId, queueType, "allow")
                                .flatMap(isAllowed -> {

                                    // 사용자가 참가열에 있는 경우
                                    if (isAllowed) {
                                        try {
                                            String json = objectMapper.writeValueAsString(Map.of(
                                                    "event", "confirmed",
                                                    "user_id", userId
                                            ));

                                            return Mono.just(ServerSentEvent.builder(json).build());
                                        } catch (JsonProcessingException ex) {
                                            return Mono.error(new ReserveException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.ALLOW_STATUS_JSON_EXCEPTION));
                                        }

                                    // 사용자가 대기열에 있는 경우 사용자의 ranking을 조회하여 반환
                                    } else {
                                        return userService.searchUserRanking(userId, queueType, "wait")
                                                .flatMap(rank -> {
                                                    try {
                                                        // 존재하지 않는 사용자 처리, 존재한다면 순위를 1부터 시작하도록 설정했기 때문에 <= 조건 사용
                                                        if (rank <= 0) {
                                                            String json = objectMapper.writeValueAsString(Map.of(
                                                                    "event", "error",
                                                                    "message", "해당 사용자는 대기열에 존재하지 않습니다."
                                                            ));

                                                            return Mono.just(ServerSentEvent.builder(json).build());
                                                        }

                                                        // 사용자의 순위 반환
                                                        String json = objectMapper.writeValueAsString(Map.of(
                                                                "event", "update",
                                                                "rank", rank
                                                        ));

                                                        return Mono.just(ServerSentEvent.builder(json).build());
                                                    } catch (JsonProcessingException ex) {
                                                        String json = "{\"event\":\"error\",\"message\":\"JSON 처리 오류\"}";
                                                        return Mono.just(ServerSentEvent.builder(json).build());
                                                    }
                                                })
                                                .onErrorResume(ex -> {
                                                    String json = "{\"event\":\"error\",\"message\":\"랭킹 조회 중 오류 발생\"}";
                                                    return Mono.just(ServerSentEvent.builder(json).build());
                                                });
                                    }
                                })
                );
    }
}