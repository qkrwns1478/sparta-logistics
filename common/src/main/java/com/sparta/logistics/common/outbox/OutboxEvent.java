package com.sparta.logistics.common.outbox;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "p_outbox", indexes = {
        @Index(name = "idx_outbox_status_created_at", columnList = "status, created_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEvent {

    static final int MAX_RETRY = 3;

    @Id
    private UUID id;

    @Column(nullable = false)
    private String topic;

    @Column(nullable = false)
    private String aggregateId;

    @Column(nullable = false)
    private String aggregateType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxEventStatus status;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column
    private LocalDateTime processedAt;

    @Column(nullable = false)
    private int retryCount;

    public static OutboxEvent of(String topic, String aggregateId, String aggregateType, String payload) {
        OutboxEvent event = new OutboxEvent();
        event.id = UUID.randomUUID();
        event.topic = topic;
        event.aggregateId = aggregateId;
        event.aggregateType = aggregateType;
        event.payload = payload;
        event.status = OutboxEventStatus.PENDING;
        event.createdAt = LocalDateTime.now();
        event.retryCount = 0;
        return event;
    }

    /**
     * Kafka 발행 성공 시 호출
     * processedAt을 기록해 발행 완료 시점 추적 가능
     * */
    public void markSent() {
        this.status = OutboxEventStatus.SENT;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 발행 실패 시 호출
     * MAX_RETRY 도달 시 무한 재시도 방지를 위해 FAILED로 전이함
     * */
    public void incrementRetry() {
        this.retryCount++;
        if (this.retryCount >= MAX_RETRY) {
            this.status = OutboxEventStatus.FAILED;
            this.processedAt = LocalDateTime.now();
        }
    }
}
