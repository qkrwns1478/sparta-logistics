package com.sparta.logistics.delivery.exception;

import com.sparta.logistics.common.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * delivery-service 전용 예외 핸들러
 * DataIntegrityViolationException, ObjectOptimisticLockingFailureException을
 * delivery-service 범위에서만 처리한다.
 *
 * GlobalExceptionHandler(common)에는 이 두 핸들러가 없으므로 @Order 없이 단독 처리.
 */
@Slf4j
@RestControllerAdvice
public class DeliveryExceptionHandler {

    /**
     * DB UNIQUE/PK 제약 위반 예외 처리 (멱등성 safety-net)
     * 애플리케이션 레벨 중복 체크를 통과한 경우에도 동시 요청 경쟁 조건에서 발생 가능.
     * DataIntegrityViolationException → 409 Conflict 반환.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public void handleDataIntegrityViolation(
            DataIntegrityViolationException e, HttpServletRequest req) {
        log.warn("[Data Integrity Violation] method={}, uri={}, message={}",
                req.getMethod(), req.getRequestURI(), e.getMessage());
        throw new BusinessException(DeliveryErrorCode.DELIVERY_CONFLICT);
    }

    /**
     * 낙관적 락 충돌 예외 처리 (동시 수정 충돌)
     * DeliveryEntity·DeliveryManagerEntity·DeliveryRouteEntity의 @Version 충돌 시 발생. 409 Conflict 반환.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public void handleOptimisticLock(
            ObjectOptimisticLockingFailureException e, HttpServletRequest req) {
        log.warn("[Optimistic Lock] method={}, uri={}, entity={}",
                req.getMethod(), req.getRequestURI(), e.getPersistentClassName());
        throw new BusinessException(DeliveryErrorCode.DELIVERY_CONFLICT);
    }
}
