package com.sparta.logistics.delivery.dto.manager;

import java.util.UUID;

public record DeliveryManagerUpdateRequest(
        UUID hubId,
        String slackId
) {}
