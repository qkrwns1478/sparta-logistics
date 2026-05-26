package com.sparta.logistics.delivery.dto.log;

import com.sparta.logistics.delivery.entity.DeliveryLogEntity;
import com.sparta.logistics.delivery.entity.DeliveryStatus;
import com.sparta.logistics.delivery.entity.enums.DeliveryEventType;

import java.time.LocalDateTime;
import java.util.UUID;

public record DeliveryLogResponse(
        UUID logId,
        UUID deliveryId,
        DeliveryEventType eventType,
        DeliveryStatus status,
        String description,
        String location,
        UUID actorId,
        LocalDateTime recordedAt
) {
    public static DeliveryLogResponse from(DeliveryLogEntity e) {
        return new DeliveryLogResponse(
                e.getId(),
                e.getDeliveryId(),
                e.getEventType(),
                e.getStatus(),
                e.getDescription(),
                e.getLocation(),
                e.getActorId(),
                e.getRecordedAt()
        );
    }
}
