package com.sparta.logistics.order.dto;

import java.util.UUID;

/* ArchUnit 레이어 규칙에 의해 OrderService가 OrderCreateRequest에 접근하면 안 되므로 외부 레이어를 만듦 */
public record OrderItemData(
        UUID productId,
        String productName,
        Long unitPrice,
        Integer quantity
) {}
