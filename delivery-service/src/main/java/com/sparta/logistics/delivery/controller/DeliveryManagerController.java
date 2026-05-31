package com.sparta.logistics.delivery.controller;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.delivery.dto.manager.DeliveryManagerCreateRequest;
import com.sparta.logistics.delivery.dto.manager.DeliveryManagerResponse;
import com.sparta.logistics.delivery.dto.manager.DeliveryManagerSearchCond;
import com.sparta.logistics.delivery.dto.manager.DeliveryManagerStatusChangeRequest;
import com.sparta.logistics.delivery.dto.manager.DeliveryManagerUpdateRequest;
import com.sparta.logistics.delivery.service.DeliveryManagerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/delivery-managers")
public class DeliveryManagerController {

    private final DeliveryManagerService managerService;

    @PostMapping
    public ResponseEntity<ApiResponse<DeliveryManagerResponse>> createManager(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") Role role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId,
            @Valid @RequestBody DeliveryManagerCreateRequest request
    ) {
        DeliveryManagerResponse response = managerService.createManager(request, userId, role, hubId);
        return ResponseEntity.created(URI.create("/api/v1/delivery-managers/" + response.managerId()))
                .body(ApiResponse.ok("배송 담당자가 생성되었습니다.", response));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Page<DeliveryManagerResponse>>> getManagerList(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") Role role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @ModelAttribute DeliveryManagerSearchCond cond
    ) {
        return ResponseEntity.ok(ApiResponse.ok(managerService.getManagerList(userId, role, hubId, pageable, cond)));
    }

    @GetMapping("/{managerId}")
    public ResponseEntity<ApiResponse<DeliveryManagerResponse>> getManager(
            @PathVariable UUID managerId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") Role role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId
    ) {
        return ResponseEntity.ok(ApiResponse.ok(managerService.getManager(managerId, userId, role, hubId)));
    }

    @PutMapping("/{managerId}")
    public ResponseEntity<ApiResponse<DeliveryManagerResponse>> updateManager(
            @PathVariable UUID managerId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") Role role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId,
            @RequestBody DeliveryManagerUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(managerService.updateManager(managerId, request, userId, role, hubId)));
    }

    @PatchMapping("/{managerId}/status")
    public ResponseEntity<ApiResponse<DeliveryManagerResponse>> changeManagerStatus(
            @PathVariable UUID managerId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") Role role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId,
            @Valid @RequestBody DeliveryManagerStatusChangeRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.ok(managerService.changeManagerStatus(managerId, request, userId, role, hubId)));
    }

    @DeleteMapping("/{managerId}")
    public ResponseEntity<ApiResponse<Void>> deleteManager(
            @PathVariable UUID managerId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") Role role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId
    ) {
        managerService.deleteManager(managerId, userId, role, hubId);
        return ResponseEntity.ok(ApiResponse.ok("배송 담당자가 삭제되었습니다.", null));
    }
}
