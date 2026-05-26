package com.sparta.logistics.delivery.entity.enums;

public enum DeliveryManagerStatus {
    IDLE,       // 대기 중 (배송 미할당)
    WORKING,    // 배송 중 (배송 할당됨)
    INACTIVE,   // 근무 중지 (일시적 비활성)
    WITHDRAWN   // 삭제됨 (탈퇴 처리) — deletedAt과 함께 변경
}
