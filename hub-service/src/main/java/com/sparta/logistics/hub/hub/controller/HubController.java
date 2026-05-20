package com.sparta.logistics.hub.hub.controller;

import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.hub.hub.dto.request.ReqUpdateHubDto;
import com.sparta.logistics.hub.hub.dto.response.ResHubDeleteDto;
import com.sparta.logistics.hub.hub.dto.response.ResHubUpdateDto;
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
     * todo: @PreAuthorize("hasRole('MASTER')") 적용 - X-User-Role 헤더 기반 SecurityContext 세팅 필터 추가 후
     * @param request 허브 생성 요청 DTO
     * @return 생성된 허브 정보
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ResHubCreateDto>> createHub(@RequestBody @Valid ReqCreateHubDto request) {

        ResHubCreateDto response = ResHubCreateDto.from(hubService.createHub(request));
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("허브가 생성되었습니다.", response));
    }

    /**
     * 허브 단건 조회 api
     * todo: @PreAuthorize("isAuthenticated()") 적용 - X-User-Role 헤더 기반 SecurityContext 세팅 필터 추가 후
     * @param hubId 허브 ID
     * @return 허브 상세 정보
     */
    @GetMapping("/{hubId}")
    public ResponseEntity<ApiResponse<ResHubDetailDto>> getHub(@PathVariable UUID hubId) {

        ResHubDetailDto response = ResHubDetailDto.from(hubService.getHub(hubId));
        return ResponseEntity.ok(ApiResponse.ok(response));
    }

    /**
     * 허브 존재 여부 확인 api (내부 서비스 간 통신용)
     * @param hubId 허브 ID
     * @return 200 OK (존재), 404 Not Found (없음)
     */
    @GetMapping("/{hubId}/exists")
    public ResponseEntity<Void> isHubExists(@PathVariable UUID hubId) {

        boolean exists = hubService.existsHub(hubId);

        if (!exists)
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        return ResponseEntity.ok().build();
    }

    /**
     * 허브 수정 api
     * todo: @PreAuthorize("hasRole('MASTER')") 적용 - X-User-Role 헤더 기반 SecurityContext 세팅 필터 추가 후
     * @param hubId 허브 ID
     * @param request 허브 수정 요청 DTO
     * @return 수정된 허브 정보
     */
    @PutMapping("/{hubId}")
    public ResponseEntity<ApiResponse<ResHubUpdateDto>> updateHub(@PathVariable UUID hubId,
                                                                  @RequestBody @Valid ReqUpdateHubDto request) {

        ResHubUpdateDto response = ResHubUpdateDto.from(hubService.updateHub(hubId, request));

        return ResponseEntity.ok(ApiResponse.ok("허브가 수정되었습니다.", response));
    }

    /**
     * 허브 삭제 api
     * todo: @PreAuthorize("hasRole('MASTER')") 적용 - X-User-Role 헤더 기반 SecurityContext 세팅 필터 추가 후
     * @param hubId 허브 ID
     * @param userId 삭제 요청자 ID (X-User-Id 헤더)
     * @return 삭제된 허브 정보
     */
    @DeleteMapping("/{hubId}")
    public ResponseEntity<ApiResponse<ResHubDeleteDto>> deleteHub(
            @PathVariable UUID hubId,
            @RequestHeader("X-User-Id") UUID userId) {

        ResHubDeleteDto response = ResHubDeleteDto.from(hubService.deleteHub(hubId, userId));

        return ResponseEntity.ok(ApiResponse.ok("허브가 삭제되었습니다.", response));
    }
}
