package com.example.reserve;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class QueueUpdateEvent {
    private final String queueType;
}
