package com.sparta.logistics.delivery.dto.event;

import java.util.List;
import java.util.UUID;

// order-service가 발행하는 stock.reserved 이벤트 수신용 DTO
// sourceHubId 기준으로 그룹핑된 단일 이벤트 — 이벤트 1개 = 배송 1개
// 실제 order-service 발행 구조와 필드명 동기화 필요
public record StockReservedEventDto(
        UUID orderId,
        UUID receiverId,
        UUID sourceHubId,        // 출발 허브 (order-service가 그룹핑 후 발행)
        UUID destinationHubId,   // 도착 허브
        String deliveryAddress,
        String sourceHubName,
        String destinationHubName,
        List<StockReservedItemPayload> orderItems,  // 이 허브 소속 주문 상품 목록
        Integer totalDeliveryCount
) {}
