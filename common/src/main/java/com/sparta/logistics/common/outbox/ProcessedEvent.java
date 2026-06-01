package com.sparta.logistics.common.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_processed_event", indexes = {
        @Index(name = "idx_processed_event_topic", columnList = "topic")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

    @Id
    private UUID eventId;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false, updatable = false)
    private LocalDateTime processedAt;

    public static ProcessedEvent of(UUID eventId, String topic) {
        ProcessedEvent e = new ProcessedEvent();
        e.eventId = eventId;
        e.topic = topic;
        e.processedAt = LocalDateTime.now();
        return e;
    }
}
