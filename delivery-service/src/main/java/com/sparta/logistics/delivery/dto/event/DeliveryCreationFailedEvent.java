package com.sparta.logistics.delivery.dto.event;

import java.util.UUID;

public record DeliveryCreationFailedEvent(
        UUID orderId,
        String reason
) {}
