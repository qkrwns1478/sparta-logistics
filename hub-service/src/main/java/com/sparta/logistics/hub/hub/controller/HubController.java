package com.sparta.logistics.hub.hub.controller;

import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.hub.hub.dto.request.ReqCreateHubDto;
import com.sparta.logistics.hub.hub.dto.response.ResHubCreateDto;
import com.sparta.logistics.hub.hub.dto.response.ResHubDetailDto;
import com.sparta.logistics.hub.hub.service.HubService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/hubs")
@RequiredArgsConstructor
public class HubController {

    private final HubService hubService;

    /**
     * 허브 생성 api
     * @param ReqCreateHubDto
     * @return ResHubCreateDto
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ResHubCreateDto>> createHub(@RequestBody @Valid ReqCreateHubDto request) {

        ResHubCreateDto response = ResHubCreateDto.from(hubService.createHub(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("허브가 생성되었습니다.", response));
    }

    @GetMapping("/{hubId}")
    public ResponseEntity<ApiResponse<ResHubDetailDto>> getHub(@PathVariable UUID hubId) {

        ResHubDetailDto response = ResHubDetailDto.from(hubService.getHub(hubId));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    @GetMapping("/{hubId}/exists")
    public ResponseEntity<Void> isHubExists(@PathVariable UUID hubId) {

        boolean exists = hubService.existsHub(hubId);

        if (!exists)
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        return ResponseEntity.ok().build();
    }
}
