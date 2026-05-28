package com.sparta.logistics.delivery.controller;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.delivery.dto.route.DeliveryRouteResponse;
import com.sparta.logistics.delivery.service.DeliveryRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/delivery-routes")
public class DeliveryRouteMgmtController {

    private final DeliveryRouteService routeService;

    // 경로 담당자 강제 재배정
    @PatchMapping("/{routeId}/reassign")
    public ResponseEntity<DeliveryRouteResponse> reassignManager(
            @PathVariable UUID routeId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") Role role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId
    ) {
        return ResponseEntity.ok(routeService.reassignManager(routeId, userId, role, hubId));
    }
}
