package com.sparta.logistics.delivery.controller;

import com.sparta.logistics.delivery.dto.DeliveryDetailResponse;
import com.sparta.logistics.delivery.dto.DeliveryListResponse;
import com.sparta.logistics.delivery.dto.DeliverySearchCond;
import com.sparta.logistics.delivery.dto.DeliveryStatusChangeRequest;
import com.sparta.logistics.delivery.dto.DeliveryUpdateRequest;
import com.sparta.logistics.delivery.service.DeliveryService;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/deliveries")
public class DeliveryController {

    private final DeliveryService deliveryService;

    // 배송 단건 조회 (권한 검사 포함)
    @GetMapping("/{deliveryId}")
    public ResponseEntity<DeliveryDetailResponse> getDelivery(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId,
            @RequestHeader(value = "X-User-CompanyId", required = false) UUID companyId
    ) {
        return ResponseEntity.ok(deliveryService.getDelivery(deliveryId, userId, role, hubId, companyId));
    }

    // 배송 목록 조회
    @GetMapping
    public ResponseEntity<Page<DeliveryListResponse>> getDeliveryList(
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId,
            @PageableDefault(size = 10, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @ModelAttribute DeliverySearchCond cond
    ) {
        return ResponseEntity.ok(deliveryService.getDeliveryList(userId, role, hubId, pageable, cond));
    }

    // 배송 수정
    @PutMapping("/{deliveryId}")
    public ResponseEntity<DeliveryDetailResponse> updateDelivery(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId,
            @RequestBody DeliveryUpdateRequest request
    ) {
        return ResponseEntity.ok(deliveryService.updateDelivery(deliveryId, request, userId, role, hubId));
    }

    // 배송 상태 변경
    @PatchMapping("/{deliveryId}/status")
    public ResponseEntity<DeliveryDetailResponse> changeStatus(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role,
            @RequestHeader(value = "X-User-HubId", required = false) UUID hubId,
            @Valid @RequestBody DeliveryStatusChangeRequest request
    ) {
        return ResponseEntity.ok(deliveryService.changeStatus(deliveryId, request, userId, role, hubId));
    }

    // 배송 삭제 (MASTER만)
    @DeleteMapping("/{deliveryId}")
    public ResponseEntity<Void> deleteDelivery(
            @PathVariable UUID deliveryId,
            @RequestHeader("X-User-Id") UUID userId,
            @RequestHeader("X-User-Role") String role
    ) {
        deliveryService.deleteDelivery(deliveryId, userId, role);
        return ResponseEntity.noContent().build();
    }

    // 배송 생성은 Kafka stock.reserved 이벤트를 통해 자동 생성됨 (DeliveryEventHandler 참고)
}
