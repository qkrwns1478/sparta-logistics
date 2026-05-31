package com.sparta.logistics.delivery.dto.event;

import java.util.UUID;

// stock.reserved 이벤트 내 개별 주문 상품 항목
public record StockReservedItemPayload(
        UUID orderItemId,       // 원래 주문 항목 ID (hub-service가 발행, delivery.started까지 전파)
        UUID productId,
        UUID sourceHubId,
        int reservedQuantity
) {}
