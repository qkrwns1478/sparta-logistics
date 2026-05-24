package com.sparta.logistics.delivery.dto.event;

import java.time.LocalDateTime;
import java.util.UUID;

public record AiDeadlineCalculatedEvent(
        UUID deliveryId,
        LocalDateTime finalDispatchDeadlineAt
) {}
