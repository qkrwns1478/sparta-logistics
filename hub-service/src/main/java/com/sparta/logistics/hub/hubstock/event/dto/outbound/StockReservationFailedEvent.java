package com.sparta.logistics.hub.hubstock.event.dto.outbound;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.UUID;

@Getter
@AllArgsConstructor
public class StockReservationFailedEvent {
    private UUID orderId;
    private UUID productId;
    private String reason;
}
