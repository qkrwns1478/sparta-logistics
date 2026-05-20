package com.sparta.logistics.order.dto.request;

import jakarta.annotation.Nullable;
import lombok.Getter;

@Getter
public class OrderCancelRequest {
    @Nullable
    private String cancelReason;
}
