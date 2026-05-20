package com.sparta.logistics.hub.hubroute.controller;

import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.hub.hubroute.dto.request.CreateHubRouteRequest;
import com.sparta.logistics.hub.hubroute.dto.response.HubRouteCreateResponse;
import com.sparta.logistics.hub.hubroute.dto.response.HubRouteDetailResponse;
import com.sparta.logistics.hub.hubroute.dto.response.HubRouteListResponse;
import com.sparta.logistics.hub.hubroute.service.HubRouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hub-routes")
@RequiredArgsConstructor
public class HubRouteController {

    private final HubRouteService hubRouteService;

    @PostMapping
    public ResponseEntity<ApiResponse<HubRouteCreateResponse>> createHubRoute(
            @RequestBody @Valid CreateHubRouteRequest request) {

        HubRouteCreateResponse response = hubRouteService.createHubRoute(request);

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
}
