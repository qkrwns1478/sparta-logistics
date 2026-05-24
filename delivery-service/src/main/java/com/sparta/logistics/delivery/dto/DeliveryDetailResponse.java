package com.sparta.logistics.delivery.dto;

import com.sparta.logistics.delivery.entity.DeliveryEntity;
import com.sparta.logistics.delivery.entity.DeliveryStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record DeliveryDetailResponse(
        UUID deliveryId,
        UUID orderId,
        DeliveryStatus status,
        UUID sourceHubId,
        UUID destinationHubId,
        UUID currentHubId,
        String deliveryAddress,
        UUID receiverId,
        String receiverSlackId,
        UUID companyDeliveryManagerId,
        LocalDateTime finalDispatchDeadlineAt,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
    public static DeliveryDetailResponse from(DeliveryEntity e) {
        return new DeliveryDetailResponse(
                e.getId(),
                e.getOrderId(),
                e.getStatus(),
                e.getSourceHubId(),
                e.getDestinationHubId(),
                e.getCurrentHubId(),
                e.getDeliveryAddress(),
                e.getReceiverId(),
                e.getReceiverSlackId(),
                e.getCompanyDeliveryManagerId(),
                e.getFinalDispatchDeadlineAt(),
                e.getStartedAt(),
                e.getCompletedAt()
        );
    }
}
