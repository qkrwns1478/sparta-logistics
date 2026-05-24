package com.sparta.logistics.delivery.dto;

import java.util.UUID;

public record DeliveryCreateRequest(
        // TODO: 생성 요청 파라미터 정의
        UUID orderId,
        UUID receiverId,
        String address,
        String receiverName,
        String phoneNumber
) {}