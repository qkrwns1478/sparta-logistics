package com.sparta.logistics.order.order.lock;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.order.exception.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class OrderLockManager {

    private final StringRedisTemplate redisTemplate;

    private static final String LOCK_PREFIX = "lock:order:";
    private static final String STATUS_PREFIX = "status:order:";
    private static final String RETRY_PREFIX = "retry:order:restore:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(30);
    private static final Duration RETRY_TTL = Duration.ofHours(2);
    public  static final int MAX_RESTORE_RETRY = 3;

    /** 분산 락 획득: 이미 잠겨 있으면 ORDER_LOCK_CONFLICT 예외 발생 **/
    public void acquireLock(UUID orderId) {
        String key = LOCK_PREFIX + orderId;
        Boolean acquired = redisTemplate.opsForValue()
                .setIfAbsent(key, "1", LOCK_TTL);
        if (!Boolean.TRUE.equals(acquired)) {
            throw new BusinessException(OrderErrorCode.ORDER_LOCK_CONFLICT);
        }
    }

    /** 분산 락 해제 **/
    public void releaseLock(UUID orderId) {
        redisTemplate.delete(LOCK_PREFIX + orderId);
    }

    /** 상태키 조회: 키가 없으면 empty 반환 **/
    public Optional<OrderProcessStatus> getStatusKey(UUID orderId) {
        String val = redisTemplate.opsForValue().get(STATUS_PREFIX + orderId);
        if (val == null) return Optional.empty();
        return Optional.of(OrderProcessStatus.valueOf(val));
    }

    /** 상태키 세팅 **/
    public void setStatusKey(UUID orderId, OrderProcessStatus status) {
        redisTemplate.opsForValue().set(STATUS_PREFIX + orderId, status.name(), LOCK_TTL);
    }

    /** 상태키 삭제 **/
    public void clearStatusKey(UUID orderId) {
        redisTemplate.delete(STATUS_PREFIX + orderId);
    }

    /** restore.stock.command 재시도 횟수 증가 후 현재 값 반환 **/
    public int incrementAndGetRestoreRetry(UUID orderId) {
        Long count = redisTemplate.opsForValue().increment(RETRY_PREFIX + orderId);
        if (count == null) {
            return 1;
        }
        if (count == 1) {
            redisTemplate.expire(RETRY_PREFIX + orderId, RETRY_TTL);
        }
        return count.intValue();
    }

    /** restore 재시도 카운터 삭제 (Saga 종료 시 호출) **/
    public void clearRestoreRetry(UUID orderId) {
        redisTemplate.delete(RETRY_PREFIX + orderId);
    }
}
