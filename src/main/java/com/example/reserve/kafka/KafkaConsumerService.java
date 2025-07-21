package com.example.reserve.kafka;

import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
public class KafkaConsumerService {

    @KafkaListener(topics = "queueing-system", groupId = "consumer_group01")
    public void consume(String message) throws IOException {
        log.info("returned message : {}", message);
    }
}
