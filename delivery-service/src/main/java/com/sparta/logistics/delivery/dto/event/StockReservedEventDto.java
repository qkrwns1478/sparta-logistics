package com.sparta.logistics.delivery.dto.event;

import java.util.UUID;

// hub-service가 발행하는 stock.reserved 이벤트 수신용 DTO
// 실제 hub-service 발행 구조와 필드명 동기화 필요
public record StockReservedEventDto(
        UUID orderId,
        UUID receiverId,
        UUID sourceHubId,
        UUID destinationHubId,
        String deliveryAddress
) {}
