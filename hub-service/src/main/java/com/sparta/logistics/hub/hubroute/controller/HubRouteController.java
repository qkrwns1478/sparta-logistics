package com.sparta.logistics.hub.hubroute.controller;

import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.hub.hubroute.dto.request.CreateHubRouteRequest;
import com.sparta.logistics.hub.hubroute.dto.response.HubRouteCreateResponse;
import com.sparta.logistics.hub.hubroute.service.HubRouteService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
