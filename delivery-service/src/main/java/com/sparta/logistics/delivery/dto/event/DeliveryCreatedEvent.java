package com.sparta.logistics.delivery.dto.event;

import java.util.UUID;

// 배송 생성 성공 시 발행 — AI-service(발송 시한 계산) 및 배차 자동화 트리거에 사용
public record DeliveryCreatedEvent(
        UUID deliveryId,
        UUID orderId
) {}
