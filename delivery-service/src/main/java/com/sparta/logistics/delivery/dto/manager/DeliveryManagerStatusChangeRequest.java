package com.sparta.logistics.delivery.dto.manager;

import com.sparta.logistics.delivery.entity.enums.DeliveryManagerStatus;
import jakarta.validation.constraints.NotNull;

public record DeliveryManagerStatusChangeRequest(
        @NotNull DeliveryManagerStatus status
) {}
