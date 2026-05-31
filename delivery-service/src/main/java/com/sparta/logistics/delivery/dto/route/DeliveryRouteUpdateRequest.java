package com.sparta.logistics.delivery.dto.route;

import com.sparta.logistics.delivery.entity.enums.RouteStatus;

import java.math.BigDecimal;

public record DeliveryRouteUpdateRequest(
        RouteStatus status,
        BigDecimal actualDistance,
        Integer actualDuration
) {}
