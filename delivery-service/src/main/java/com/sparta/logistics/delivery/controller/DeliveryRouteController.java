package com.sparta.logistics.delivery.controller;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.delivery.dto.log.DeliveryLogResponse;
import com.sparta.logistics.delivery.dto.route.DeliveryRouteResponse;
import com.sparta.logistics.delivery.dto.route.DeliveryRouteUpdateRequest;
import com.sparta.logistics.delivery.service.DeliveryLogService;
import com.sparta.logistics.delivery.service.DeliveryRouteService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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

    @GetMapping("/routes")
    public ResponseEntity<ApiResponse<List<DeliveryRouteResponse>>> getRouteList(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") Role role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId,
            @RequestHeader(value = "X-User-CompanyId", required = false) UUID companyId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(routeService.getRouteList(deliveryId, userId, role, hubId, companyId)));
    }

    @PutMapping("/routes/{routeId}")
    public ResponseEntity<ApiResponse<DeliveryRouteResponse>> updateRoute(
            @PathVariable UUID deliveryId,
            @PathVariable UUID routeId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") Role role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId,
            @RequestBody DeliveryRouteUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(routeService.updateRoute(deliveryId, routeId, request, userId, role, hubId)));
    }

    @GetMapping("/logs")
    public ResponseEntity<ApiResponse<List<DeliveryLogResponse>>> getLogs(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") Role role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId,
            @RequestHeader(value = "X-User-CompanyId", required = false) UUID companyId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(logService.getLogs(deliveryId, userId, role, hubId, companyId)));
    }

    @PatchMapping("/routes/{routeId}/reassign")
    public ResponseEntity<ApiResponse<DeliveryRouteResponse>> reassignManager(
            @PathVariable UUID deliveryId,
            @PathVariable UUID routeId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") Role role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(routeService.reassignManager(routeId, userId, role, hubId)));
    }
}
