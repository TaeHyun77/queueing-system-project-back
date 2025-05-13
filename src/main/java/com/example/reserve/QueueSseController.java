package com.example.reserve;

import com.example.reserve.exception.ErrorCode;
import com.example.reserve.exception.ReserveException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
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

@RestController
@RequiredArgsConstructor
public class QueueSseController {

    private final UserService userService;
    private final Sinks.Many<QueueEventPayload> sink = Sinks.many().multicast().onBackpressureBuffer();

    // QueueUpdateEvent 발생 시 sink를 통해 SSE에 연결된 모든 클라이언트에서 알림이 push
    @EventListener
    public void handleQueueUpdated(QueueUpdateEvent event) {
        sink.tryEmitNext(new QueueEventPayload(event.getQueueType()));
    }

    // 클라이언트가 서버와 SSE 스트림 연결
    @GetMapping(value = "/queue/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<String>> streamQueue(@RequestParam String userId, @RequestParam String queueType) {
        ObjectMapper objectMapper = new ObjectMapper();

        // 최초 연결 시
        Mono<ServerSentEvent<String>> initialEvent =
                userService.isAllowedUser(userId, queueType)
                        .flatMap(allowed -> {
                            String json;
                            try {
                                if (allowed) {
                                    json = objectMapper.writeValueAsString(Map.of(
                                            "event", "confirmed",
                                            "user_id", userId
                                    ));
                                    return Mono.just(ServerSentEvent.builder(json).build());
                                } else {
                                    return userService.searchUserRanking(userId, queueType)
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

        // 이후 실시간 이벤트 처리
        Flux<ServerSentEvent<String>> streamEvents = sink.asFlux()
                .filter(e -> e.getQueueType().equals(queueType))
                .flatMap(e ->
                        userService.isAllowedUser(userId, queueType)
                                .flatMap(allowed -> {
                                    if (allowed) {
                                        String json = null;
                                        try {
                                            json = objectMapper.writeValueAsString(Map.of(
                                                    "event", "confirmed"
                                            ));
                                        } catch (JsonProcessingException ex) {
                                            return Mono.error(new ReserveException(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.ALLOW_STATUS_JSON_EXCEPTION));
                                        }
                                        return Mono.just(ServerSentEvent.builder(json).build());
                                    } else {
                                        return userService.searchUserRanking(userId, queueType)
                                                .map(rank -> {
                                                    String json = null;
                                                    try {
                                                        json = objectMapper.writeValueAsString(Map.of(
                                                                "event", "update",
                                                                "rank", rank
                                                        ));
                                                    } catch (JsonProcessingException ex) {
                                                        throw new RuntimeException(ex);
                                                    }
                                                    return ServerSentEvent.builder(json).build();
                                                });
                                    }
                                })
                );

        return Flux.merge(initialEvent, streamEvents);
    }



}

