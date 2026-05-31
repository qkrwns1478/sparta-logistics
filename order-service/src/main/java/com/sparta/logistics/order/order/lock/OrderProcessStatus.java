package com.sparta.logistics.order.order.lock;

public enum OrderProcessStatus {
    PROCESSING,  // 주문 처리 중 (PENDING → ACCEPTED → IN_DELIVERY)
    CANCELLING   // 취소 진행 중
}
