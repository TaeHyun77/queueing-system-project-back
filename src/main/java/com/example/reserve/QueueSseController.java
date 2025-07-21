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
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class QueueSseController {

    private final UserService userService;

    /* 4
     * sink : 이벤트를 push 하는 통로, 여기서는 QueueEventPayload 타입의 통로를 만든 것
     * replay() : 마지막으로 발생한 이벤트를 캐싱해 두었다가, 나중에 구독한 사용자에게도 해당 이벤트를 재전달할 수 있는 방식
     * */
    private final Sinks.Many<QueueEventPayload> sink = Sinks.many().replay().limit(1);

    /* 5
     * 서비스 코드에서 대기열 순위에 변동이 발생하면 eventPublisher.publishEvent(new QueueUpdateEvent(queueType)) 등을 통해 ⭐특정 queueType에
     * , 이벤트가 발생했다는 것을 알림 → @EventListener가 이를 받아서 내부에서 sink.tryEmitNext(...)를 호출하면 QueueEventPayload 이벤트를 발행
     * , → QueueEventPayload를 구독한 클라이언트들은 sink.asFlux()를 통해 실시간으로 이벤트를 전달받음
     * */
    @EventListener
    public void handleQueueUpdated(QueueUpdateEvent event) {

        // 대기열 상태에 변동을 일으켰을 때 서버에서 이 메서드로 이벤트를 발행
        sink.tryEmitNext(new QueueEventPayload(event.getQueueType()));
    }

    /* 1
     * 서버와 클라이언트가 SSE 스트림 연결
     * produces = MediaType.TEXT_EVENT_STREAM_VALUE : text/event-stream 타입의 응답을 반환할 것을 의미
     * ServerSentEvent<String> : SSE 형식의 메시지를 의미, Flux<>로 감쌌으므로 여러 개의 sse 메세지를 실시간으로 전송한다는 뜻
     * */
    @GetMapping(value = "/queue/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamQueue(@RequestParam String userId, @RequestParam String queueType) {
        ObjectMapper objectMapper = new ObjectMapper();

        /* 2
         * SSE 연결 시 1회만 전송되는 초기 메시지
         * 클라이언트와 처음 연결할 때 사용자가 '참가열'에 있는지 먼저 확인
         * 참가열에 있다면 → 'confirmed' 이벤트와 입력한 사용자의 이름 반환
         * 대기열에 있다면 → 대기열 내의 사용자 ranking 반환
         * */
        Mono<ServerSentEvent<String>> initialEvent =
                userService.isExistUserInWaitOrAllow(userId, queueType, "allow") // 대기열 및 참가열에 사용자가 존재하는지 확인
                        .flatMap(allowed -> {
                            String json;

                            try {
                                // 참가열에 있다면 → 'confirmed' 이벤트와 입력한 사용자의 이름 반환
                                if (allowed) {
                                    log.info("##############1");

                                    json = objectMapper.writeValueAsString(Map.of(
                                            "event", "confirmed",
                                            "user_id", userId
                                    ));

                                    return Mono.just(ServerSentEvent.builder(json).build());

                                    // 대기열에 있다면 → 대기열 내의 사용자 ranking 반환
                                } else {
                                    log.info("##############2");

                                    return userService.searchUserRanking(userId, queueType, "wait")
                                            .map(rank -> {
                                                try {
                                                    String updateJson = objectMapper.writeValueAsString(Map.of(
                                                            "event", "update",
                                                            "rank", rank
                                                    ));

                                                    return ServerSentEvent.builder(updateJson).build();
                                                } catch (JsonProcessingException e) {
                                                    throw new RuntimeException(e);
                                                }
                                            });
                                }
                            } catch (JsonProcessingException e) {
                                return Mono.error(new RuntimeException("JSON 변환 실패", e));
                            }
                        });

        /* 3
         * 서버에서 sink.tryEmitNext() 호출할 때마다 발생하는 실시간 메시지
         * sink.asFlux() : 서버 내부에서 발생하는 모든 이벤트를 구독, filter를 통해 모든 이벤트 중 클라이언트가 요청한 queueType과 일치하는 이벤트만 골라서 처리
         * */
        log.info("구독");
        Flux<ServerSentEvent<String>> streamEvents = sink.asFlux()
                .filter(e -> e.getQueueType().equals(queueType))
                .flatMap(e ->
                        userService.isExistUserInWaitOrAllow(userId, queueType, "allow")
                                .flatMap(allowed -> {
                                    if (allowed) {
                                        log.info("##############3");

                                        try {
                                            String json = objectMapper.writeValueAsString(Map.of(
                                                    "event", "confirmed",
                                                    "user_id", userId
                                            ));

                                            return Mono.just(ServerSentEvent.builder(json).build());
                                        } catch (JsonProcessingException ex) {
                                            return Mono.error(new ReserveException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.ALLOW_STATUS_JSON_EXCEPTION));
                                        }

                                    } else {
                                        log.info("##############4");

                                        return userService.searchUserRanking(userId, queueType, "wait")
                                                .flatMap(rank -> {
                                                    try {
                                                        // 존재하지 않는 사용자 처리
                                                        if (rank <= 0) {
                                                            String json = objectMapper.writeValueAsString(Map.of(
                                                                    "event", "error",
                                                                    "message", "사용자가 대기열에서 제거되었습니다."
                                                            ));
                                                            return Mono.just(ServerSentEvent.builder(json).build());
                                                        }

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

        return Flux.merge(initialEvent, streamEvents);
    }
}

/* 흐름 정리
 *
 * 1번에서 서버와 클라이언트가 sse 연결을 하고 나면 3번에서 sink.asFlux()를 통해 발생하는 모든 이벤트를 받지만 filter를 통해 사용자의 queueType에 맞는 이벤트만 수신하게 됨
 *
 * service 코드에서 대기열에 변동이 발생하면 eventPublisher.publishEvent(new QueueUpdateEvent(queueType))를 통해 특정 queueType에 이벤트가 발생하였다는 것을
 * , @EventListener가 수신하여 sink.tryEmitNext(new QueueEventPayload(event.getQueueType()))를 통해 특정 queueType의 이벤트를 발생 시키고
 * , 이렇게 발행된 이벤트는 `sink.asFlux()`를 구독 중인 클라이언트 중 해당 queueType을 필터링한 클라이언트에게만 Flux<ServerSentEvent<String>> 형태로 전달되며
 * , 최종적으로 서버 ➝ 클라이언트 방향으로 실시간 메시지가 전송됩니다.
 *
 * 즉, 클라이언트 연결 (/queue/stream) ➝ 초기 상태 전달 (Mono<ServerSentEvent<String>>) ➝ SSE 연결 유지 ➝ sink.asFlux()로 실시간 이벤트 구독 시작
 * ➝ 서버 이벤트 발생 (sink.tryEmitNext) ➝ 필터 통과 시 클라이언트에 실시간 메시지 전송
 * */