package com.sparta.logistics.delivery.dto;

import com.sparta.logistics.delivery.entity.DeliveryEntity;
import com.sparta.logistics.delivery.entity.enums.DeliveryStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeliveryListResponse {

    private UUID deliveryId;
    private UUID orderId;
    private DeliveryStatus status;
    private String sourceHubName;
    private String destinationHubName;
    private String deliveryManagerName;
    private LocalDateTime createdAt;

    public static DeliveryListResponse from(DeliveryEntity delivery, String sourceHubName, String destinationHubName, String managerName) {
        return DeliveryListResponse.builder()
                .deliveryId(delivery.getId())
                .orderId(delivery.getOrderId())
                .status(delivery.getStatus())
                .sourceHubName(sourceHubName)
                .destinationHubName(destinationHubName)
                .deliveryManagerName(managerName)
                .createdAt(delivery.getCreatedAt())
                .build();
    }
}
