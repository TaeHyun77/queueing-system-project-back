package com.example.reserve;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.LocalDate;

@Getter
@RequiredArgsConstructor
public class QueueUpdateEvent {
    private final String queueType;
}
