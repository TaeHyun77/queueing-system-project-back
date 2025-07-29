package com.example.reserve.kafka;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class DebeziumKafkaMessage {
    private Payload payload;

    @Getter
    @Setter
    @NoArgsConstructor
    public static class Payload {
        private Long id;
        private String queue_type;
        private String status;
        private String user_id;
    }
}
