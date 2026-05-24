package com.sparta.logistics.delivery.controller;

import com.sparta.logistics.delivery.dto.log.DeliveryLogResponse;
import com.sparta.logistics.delivery.dto.route.DeliveryRouteResponse;
import com.sparta.logistics.delivery.dto.route.DeliveryRouteUpdateRequest;
import com.sparta.logistics.delivery.service.DeliveryLogService;
import com.sparta.logistics.delivery.service.DeliveryRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/deliveries/{deliveryId}")
public class DeliveryRouteController {

    private final DeliveryRouteService routeService;
    private final DeliveryLogService logService;

    // 배송경로 목록 조회
    @GetMapping("/routes")
    public ResponseEntity<List<DeliveryRouteResponse>> getRouteList(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId,
            @RequestHeader(value = "X-User-CompanyId", required = false) UUID companyId
    ) {
        return ResponseEntity.ok(routeService.getRouteList(deliveryId, userId, role, hubId, companyId));
    }

    // 배송경로 수정
    @PutMapping("/routes/{routeId}")
    public ResponseEntity<DeliveryRouteResponse> updateRoute(
            @PathVariable UUID deliveryId,
            @PathVariable UUID routeId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId,
            @RequestBody DeliveryRouteUpdateRequest request
    ) {
        return ResponseEntity.ok(routeService.updateRoute(deliveryId, routeId, request, userId, role, hubId));
    }

    // 배송 이벤트 로그 조회
    @GetMapping("/logs")
    public ResponseEntity<List<DeliveryLogResponse>> getLogs(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId,
            @RequestHeader(value = "X-User-CompanyId", required = false) UUID companyId
    ) {
        return ResponseEntity.ok(logService.getLogs(deliveryId, userId, role, hubId, companyId));
    }
}
