package com.example.reserve.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

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

    public ResponseEntity<Void> sendMessageToKafka(String queueType, String message) {

        log.info("sending message : {} to {}", message, queueType);

        kafkaTemplate.send(topicName, queueType, message);

        return ResponseEntity.ok().build();
    }

    public void sendMessage(String topic, String key, String userId) {
        try {
            long enterTimestamp = Instant.now().toEpochMilli();

            QueueMessage message = new QueueMessage(userId, enterTimestamp);
            String json = objectMapper.writeValueAsString(message);

            kafkaTemplate.send(topic, key, json).whenComplete((result, ex) -> {
                if (ex == null) {
                    log.info("Kafka 전송 성공: {}", json);
                } else {
                    log.error("Kafka 전송 실패: {}", ex.getMessage());
                }
            });
        } catch (JsonProcessingException e) {
            log.error("직렬화 실패: {}", e.getMessage());
        }
    }
}
