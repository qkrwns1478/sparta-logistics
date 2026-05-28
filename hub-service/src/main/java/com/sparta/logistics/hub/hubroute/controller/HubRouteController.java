package com.sparta.logistics.hub.hubroute.controller;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.hub.hubroute.dto.request.CreateHubRouteRequest;
import com.sparta.logistics.hub.hubroute.dto.request.UpdateHubRouteRequest;
import com.sparta.logistics.hub.hubroute.dto.response.*;
import com.sparta.logistics.hub.hubroute.service.HubRouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hub-routes")
@RequiredArgsConstructor
public class HubRouteController {

    private final HubRouteService hubRouteService;

    @PostMapping
    public ResponseEntity<ApiResponse<HubRouteDetailResponse>> createHubRoute(
            @RequestBody @Valid CreateHubRouteRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") Role role) {

        HubRouteDetailResponse response = hubRouteService.createHubRoute(request, role);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("허브 경로가 생성되었습니다.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<HubRouteListResponse>>> getHubRouteList(
            @RequestParam(required = false) UUID sourceHubId,
            @RequestParam(required = false) UUID destinationHubId,
            Pageable pageable) {

        Page<HubRouteListResponse> response = hubRouteService
                .getHubRouteList(sourceHubId, destinationHubId, pageable);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{routeId}")
    public ResponseEntity<ApiResponse<HubRouteDetailResponse>> getHubRoute(@PathVariable UUID routeId) {

        HubRouteDetailResponse response = hubRouteService.getHubRoute(routeId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @PutMapping("/{routeId}")
    public ResponseEntity<ApiResponse<HubRouteUpdateResponse>> updateHubRoute(
            @PathVariable UUID routeId,
            @RequestBody @Valid UpdateHubRouteRequest request,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") Role role) {

        HubRouteUpdateResponse response = hubRouteService.updateHubRoute(routeId, request, role);

        return ResponseEntity.ok(ApiResponse.ok("이동 경로가 수정되었습니다.", response));
    }

    @DeleteMapping("/{routeId}")
    public ResponseEntity<ApiResponse<HubRouteDeleteResponse>> deleteHubRoute(
            @PathVariable UUID routeId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") Role role) {

        HubRouteDeleteResponse response = hubRouteService.deleteHubRoute(routeId, userId, role);

        return ResponseEntity.ok(ApiResponse.ok("이동 경로가 삭제되었습니다.", response));
    }

    @GetMapping("/segments")
    public ResponseEntity<ApiResponse<List<HubRouteSegmentResponse>>> getHubRouteSegments(
            @RequestParam UUID sourceHubId,
            @RequestParam UUID destinationHubId) {

        List<HubRouteSegmentResponse> response = hubRouteService.getHubRouteSegments(sourceHubId, destinationHubId);

        return ResponseEntity.ok(ApiResponse.ok(response));
    }
}
