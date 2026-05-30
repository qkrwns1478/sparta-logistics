package com.sparta.logistics.common.outbox;

/**
 * Outbox 이벤트 생명주기 상태
 * 모든 이벤트는 PENDING으로 시작해 SENT 또는 FAILED로 전이함
 * */
public enum OutboxEventStatus {
    PENDING,  // 릴레이 발행 대기 중
    SENT,     // Kafka 발행 완료
    FAILED    // 재시도 한도(MAX_RETRY) 초과 → 수동 확인 필요
}
