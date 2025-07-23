package com.example.reserve.kafka;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class QueueMessage {

    private String queueType;

    @Override
    public String toString() {
        return "queueType : " + queueType;
    }
}
