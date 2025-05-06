package com.example.reserve;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
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

        // 연결 되면 현재 순위 전송
        Flux<ServerSentEvent<String>> initialEvent = userService.searchUserRanking(Long.parseLong(userId), queueType)
                .map(rank -> {
                    try {
                        String json = objectMapper.writeValueAsString(Map.of(
                                "event", "update",
                                "rank", rank
                        ));
                        return ServerSentEvent.builder(json).build();
                    } catch (Exception e) {
                        return ServerSentEvent.builder("error").build();
                    }
                })
                .flux();

        // 이후 발생하는 이벤트에 대한 실시간 순위 응답
        Flux<ServerSentEvent<String>> streamEvents = sink.asFlux()
                .filter(e -> e.getQueueType().equals(queueType))
                .flatMap(e ->
                        userService.searchUserRanking(Long.parseLong(userId), queueType)
                                .map(rank -> {
                                    try {
                                        String json = objectMapper.writeValueAsString(Map.of(
                                                "event", "update",
                                                "rank", rank
                                        ));
                                        return ServerSentEvent.builder(json).build();
                                    } catch (Exception ex) {
                                        return ServerSentEvent.builder("error").build();
                                    }
                                })
                );

        // 병합
        return Flux.merge(initialEvent, streamEvents);
    }
}

