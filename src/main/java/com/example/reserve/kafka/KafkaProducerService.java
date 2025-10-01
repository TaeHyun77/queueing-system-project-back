package com.example.reserve.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;

@Slf4j
@RequiredArgsConstructor
@Service
public class KafkaProducerService {

    @Value("${topic.name}")
    private String topicName;

    private final KafkaTemplate<String,String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    public Mono<Void> sendMessage(String queueType) {
        try {
            QueueMessage message = new QueueMessage(queueType);
            String json = objectMapper.writeValueAsString(message);

            // Kafka 전송을 비동기 CompletableFuture로 시작
            CompletableFuture<SendResult<String, String>> future = kafkaTemplate.send(topicName, queueType, json);

            return Mono.fromFuture(future)
                    .doOnSuccess(result -> log.info("Kafka 전송 성공: {}", json))
                    .doOnError(ex -> log.error("Kafka 전송 실패: {}", ex.getMessage()))
                    .onErrorResume(ex -> {
                        return Mono.error(new RuntimeException("Kafka message send failed", ex));
                    })
                    .then();

        } catch (JsonProcessingException e) {
            log.error("직렬화 실패: {}", e.getMessage());
            return Mono.error(new RuntimeException("JSON serialization failed", e));
        }
    }
}
