package com.sparta.logistics.delivery.dto.manager;

import com.sparta.logistics.delivery.entity.enums.DeliveryManagerType;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record DeliveryManagerCreateRequest(
        @NotNull UUID userId,
        @NotNull UUID hubId,
        String slackId,
        @NotNull DeliveryManagerType managerType
) {}
