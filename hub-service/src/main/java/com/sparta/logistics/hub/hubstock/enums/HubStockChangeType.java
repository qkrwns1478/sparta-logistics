package com.sparta.logistics.hub.hubstock.enums;

public enum HubStockChangeType {
    INBOUND,
    ORDER_RESERVE,    // 주문 예약
    ORDER_DECREASE,   // 배송 확정 차감
    CANCEL_RESTORE,
    RETURN_RESTORE,
    MANUAL_ADJUST
}
