package com.sparta.logistics.hub.hubstock.event.dto.outbound;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;
import java.util.UUID;

@Getter
@AllArgsConstructor
public class StockReservedEvent {
    private UUID orderId;
    private UUID destinationHubId;
    private List<ReservedItem> orderItems;

    @Getter
    @AllArgsConstructor
    public static class ReservedItem {
        private UUID productId;
        private Integer reservedQuantity;
        private UUID sourceHubId;
    }
}
