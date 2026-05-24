package com.sparta.logistics.delivery.dto.manager;

import com.sparta.logistics.delivery.entity.DeliveryManagerEntity;
import com.sparta.logistics.delivery.entity.enums.DeliveryManagerStatus;
import com.sparta.logistics.delivery.entity.enums.DeliveryManagerType;

import java.time.LocalDateTime;
import java.util.UUID;

public record DeliveryManagerResponse(
        UUID managerId,
        UUID hubId,
        String slackId,
        DeliveryManagerType managerType,
        int deliverySequence,
        LocalDateTime lastAssignedAt,
        DeliveryManagerStatus status,
        LocalDateTime createdAt
) {
    public static DeliveryManagerResponse from(DeliveryManagerEntity e) {
        return new DeliveryManagerResponse(
                e.getId(),
                e.getHubId(),
                e.getSlackId(),
                e.getManagerType(),
                e.getDeliverySequence(),
                e.getLastAssignedAt(),
                e.getStatus(),
                e.getCreatedAt()
        );
    }
}
