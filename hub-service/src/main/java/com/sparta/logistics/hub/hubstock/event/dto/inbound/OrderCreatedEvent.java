package com.sparta.logistics.hub.hubstock.event.dto.inbound;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Getter
@NoArgsConstructor
public class OrderCreatedEvent {
    private UUID orderId;
    private UUID requesterCompanyId;
    private List<OrderItem> orderItems;

    @Getter
    @NoArgsConstructor
    public static class OrderItem {
        private UUID productId;
        private Integer quantity;
        private UUID hubId;
    }
}
