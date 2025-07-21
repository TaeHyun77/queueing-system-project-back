package com.example.reserve.kafka;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class QueueMessage {

    private String userId;

    private long enterTimestamp;

    @Override
    public String toString() {
        return "userId : " + userId + " , " + "enterTimestamp : " + enterTimestamp;
    }
}
