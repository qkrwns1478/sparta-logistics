package com.sparta.logistics.delivery.entity.enums;

import java.util.Map;
import java.util.Set;

public enum RouteStatus {
    WAITING,    // 이동 대기 중
    IN_TRANSIT, // 이동 중
    ARRIVED,    // 도착 완료
    CANCELLED;  // 취소됨

    private static final Map<RouteStatus, Set<RouteStatus>> ALLOWED = Map.of(
            WAITING,    Set.of(IN_TRANSIT, CANCELLED),
            IN_TRANSIT, Set.of(ARRIVED, CANCELLED),
            ARRIVED,    Set.of(),
            CANCELLED,  Set.of()
    );

    public boolean canTransitionTo(RouteStatus next) {
        return ALLOWED.getOrDefault(this, Set.of()).contains(next);
    }
}
