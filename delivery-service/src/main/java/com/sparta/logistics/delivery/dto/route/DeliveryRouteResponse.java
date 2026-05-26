package com.sparta.logistics.delivery.dto.route;

import com.sparta.logistics.delivery.entity.DeliveryRouteEntity;
import com.sparta.logistics.delivery.entity.enums.RouteStatus;
import com.sparta.logistics.delivery.entity.enums.RouteType;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

public record DeliveryRouteResponse(
        UUID routeId,
        UUID deliveryId,
        int sequence,
        RouteType routeType,
        UUID sourceHubId,
        UUID destinationHubId,
        BigDecimal estimatedDistance,
        int estimatedDuration,
        BigDecimal actualDistance,
        Integer actualDuration,
        RouteStatus status,
        UUID hubDeliveryManagerId,
        LocalDateTime startedAt,
        LocalDateTime arrivedAt
) {
    public static DeliveryRouteResponse from(DeliveryRouteEntity e) {
        return new DeliveryRouteResponse(
                e.getId(),
                e.getDelivery().getId(),
                e.getSequence(),
                e.getRouteType(),
                e.getSourceHubId(),
                e.getDestinationHubId(),
                e.getEstimatedDistance(),
                e.getEstimatedDuration(),
                e.getActualDistance(),
                e.getActualDuration(),
                e.getStatus(),
                e.getHubDeliveryManagerId(),
                e.getStartedAt(),
                e.getArrivedAt()
        );
    }
}
