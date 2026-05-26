package com.sparta.logistics.delivery.dto;

import com.sparta.logistics.delivery.entity.DeliveryStatus;
import jakarta.validation.constraints.NotNull;

public record DeliveryStatusChangeRequest(
        @NotNull DeliveryStatus status
) {}
