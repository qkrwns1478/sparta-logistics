package com.sparta.logistics.delivery.dto;

import java.util.UUID;

public record DeliveryUpdateRequest(
        String deliveryAddress,
        String receiverSlackId,
        UUID currentHubId,
        UUID companyDeliveryManagerId
) {}
