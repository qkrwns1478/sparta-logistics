package com.sparta.logistics.delivery.dto.event;

import java.util.UUID;

// stock.reserved 이벤트 내 개별 주문 상품 항목
public record StockReservedItemPayload(
        UUID productId,
        UUID sourceHubId,
        int reservedQuantity
) {}
