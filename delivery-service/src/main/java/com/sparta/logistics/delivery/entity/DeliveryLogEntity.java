package com.sparta.logistics.delivery.entity;

import com.sparta.logistics.delivery.entity.enums.DeliveryEventType;
import com.sparta.logistics.delivery.entity.enums.DeliveryStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

// append-only: BaseEntity 상속 제외 (soft delete 컬럼 불필요)
// 인덱스: deliveryId + recordedAt 복합만 생성
// Zipkin이 traceId를 자동 수집하므로 별도 traceId 필드 없음
@Entity
@Table(name = "p_delivery_log", indexes = {
        @Index(name = "idx_delivery_log_delivery_recorded", columnList = "delivery_id, recorded_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeliveryLogEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "delivery_id", nullable = false)
    private UUID deliveryId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DeliveryEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column
    private DeliveryStatus status; // STATUS_CHANGED 이벤트 시 p_delivery.status 값

    @Column(length = 255)
    private String description;

    @Column(length = 255)
    private String location;

    @Column
    private UUID actorId; // 시스템 자동 처리 시 null

    @Column(nullable = false)
    private LocalDateTime recordedAt;

    public DeliveryLogEntity(UUID deliveryId, DeliveryEventType eventType, DeliveryStatus status,
                             String description, String location, UUID actorId) {
        this.deliveryId = deliveryId;
        this.eventType = eventType;
        this.status = status;
        this.description = description;
        this.location = location;
        this.actorId = actorId;
        this.recordedAt = LocalDateTime.now();
    }
}
