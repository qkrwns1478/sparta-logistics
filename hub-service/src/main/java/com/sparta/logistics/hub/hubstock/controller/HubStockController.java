package com.sparta.logistics.hub.hubstock.controller;

import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.hub.hubstock.dto.request.CreateHubStockRequest;
import com.sparta.logistics.hub.hubstock.dto.response.HubStockCreateResponse;
import com.sparta.logistics.hub.hubstock.service.HubStockService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hubs/{hubId}/stocks")
@RequiredArgsConstructor
public class HubStockController {

    private final HubStockService hubStockService;

    @PostMapping
    public ResponseEntity<ApiResponse<HubStockCreateResponse>> createHubStock(
            @PathVariable UUID hubId,
            @RequestBody @Valid CreateHubStockRequest request) {

        HubStockCreateResponse response = hubStockService.createHubStock(hubId, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("허브 재고가 생성되었습니다.", response));
    }

}
