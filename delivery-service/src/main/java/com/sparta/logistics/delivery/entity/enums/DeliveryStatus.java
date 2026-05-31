package com.sparta.logistics.delivery.entity.enums;

import java.util.Map;
import java.util.Set;

public enum DeliveryStatus {
    CREATED,
    HUB_WAITING,
    HUB_MOVING,
    DESTINATION_HUB_ARRIVED,
    OUT_FOR_DELIVERY,
    COMPLETED,
    CANCELLED;

    private static final Map<DeliveryStatus, Set<DeliveryStatus>> ALLOWED = Map.of(
            CREATED,                  Set.of(HUB_WAITING, CANCELLED),
            HUB_WAITING,              Set.of(HUB_MOVING, CANCELLED),
            HUB_MOVING,               Set.of(DESTINATION_HUB_ARRIVED),
            DESTINATION_HUB_ARRIVED,  Set.of(OUT_FOR_DELIVERY),
            OUT_FOR_DELIVERY,         Set.of(COMPLETED),
            COMPLETED,                Set.of(),
            CANCELLED,                Set.of()
    );

    public boolean canTransitionTo(DeliveryStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }
}
