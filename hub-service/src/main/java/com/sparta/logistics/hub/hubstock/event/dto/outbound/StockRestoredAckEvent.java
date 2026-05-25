package com.sparta.logistics.hub.hubstock.event.dto.outbound;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class StockRestoredAckEvent {
    private UUID orderId;
}
