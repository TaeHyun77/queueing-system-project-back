package com.example.reserve.kafka;

import com.example.reserve.QueueEventPayload;
import com.example.reserve.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@RequiredArgsConstructor
@Service
public class KafkaConsumerService {

    private final ObjectMapper objectMapper;
    private final UserService userService;

    @KafkaListener(topics = "queue_system_db.queue_system_db.outbox", groupId = "queue-event-group")
    public void consume(String message) {
        try {
            DebeziumKafkaMessage kafkaMessage = objectMapper.readValue(message, DebeziumKafkaMessage.class);

            DebeziumKafkaMessage.Payload payload = kafkaMessage.getPayload();
            if (payload != null) {
                String queueType = payload.getQueue_type();
                String status = payload.getStatus();

                userService.getSink().tryEmitNext(new QueueEventPayload(queueType));
                log.info("Kafka 이벤트 수신 - queueType: {}, status: {}", queueType, status);
            }
        } catch (Exception e) {
            log.error("Kafka 메시지 소비 실패", e);
        }
    }
}
