package com.sparta.logistics.delivery.entity.enums;

public enum DeliveryEventType {
    MANAGER_ASSIGNED, // 배송 담당자 배정
    ROUTE_UPDATED,    // 배송 경로 변경
    STATUS_CHANGED,   // 배송 상태 변경
    CANCELLED,        // 배송 취소
    EXCEPTION         // 예외 상황 기록
}
