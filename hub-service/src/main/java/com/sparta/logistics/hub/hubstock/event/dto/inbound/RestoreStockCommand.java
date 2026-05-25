package com.sparta.logistics.hub.hubstock.event.dto.inbound;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class RestoreStockCommand {
    private UUID orderId;
    private List<OrderItem> orderItems;

    @Getter
    @NoArgsConstructor
    public static class OrderItem {
        private UUID productId;
        private Integer quantity;
    }
}
