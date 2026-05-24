package com.sparta.logistics.common.exception;

import com.sparta.logistics.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.stream.Collectors;

import static com.sparta.logistics.common.exception.CommonErrorCode.*;

/**
 * 전역 애플리케이션 예외 제어 핸들러
 * 컨트롤러 레이어 이하에서 발생하는 모든 예외를 인터셉트하여 일관된 API 응답을 구성한다.
 */

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 커스텀 비즈니스 로직 예외 처리
     * 서비스 레이어에서 의도적으로 발생시킨 예외에 대해 해당 ErrorCode에 맞는 상태코드를 반환한다.
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e, HttpServletRequest req) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("[BusinessException] method={}, uri={}, code={}, message={}",
                req.getMethod(), req.getRequestURI(), errorCode.getCode(), e.getMessage());
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode));
    }

    /**
     * DTO 유효성 검증(@Valid) 실패 예외 처리
     * 각 필드별 에러 메시지를 수집하여 하나로 병합한 후 400 Bad Request와 함께 반환한다.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e, HttpServletRequest req) {

        List<String> errorMessages = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> String.format("[%s: %s]", fe.getField(), fe.getDefaultMessage()))
                .collect(Collectors.toList());

        String combinedMessage = String.join(", ", errorMessages);

        log.warn("[Validation Failed] method={}, uri={}, totalErrors={}, details={}",
                req.getMethod(), req.getRequestURI(), errorMessages.size(), combinedMessage);

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(VALIDATION_FAILED, combinedMessage));
    }

    /**
     * 요청 파라미터 또는 경로 변수(@RequestParam, @PathVariable) 타입 미스매치 예외 처리
     * 주로 UUID 형식 포맷 오류, 존재하지 않는 Enum 값 요청 시 발생한다.
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest req) {
        String message = String.format("%s의 값이 올바르지 않음: %s", e.getName(), e.getValue());

        log.warn("[Type Mismatch] method={}, uri={}, message={}",
                req.getMethod(), req.getRequestURI(), message);

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(TYPE_MISMATCH, message));
    }

    /**
     * 필수 HTTP 요청 헤더 누락 예외 처리
     */
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e, HttpServletRequest req) {
        log.warn("[Missing Header] method={}, uri={}, headerName={}",
                req.getMethod(), req.getRequestURI(), e.getHeaderName());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(MISSING_REQUEST_HEADER));
    }

    /**
     * HTTP Request Body 데이터 바인딩 실패 예외 처리
     * JSON 문법 오류가 있거나 객체 매핑이 불가능한 구조일 때 발생한다.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest req) {
        log.warn("[Invalid Body] method={}, uri={}, message={}",
                req.getMethod(), req.getRequestURI(), e.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(INVALID_REQUEST_BODY));
    }

    /**
     * DB UNIQUE/PK 제약 위반 예외 처리 (멱등성 safety-net)
     * 애플리케이션 레벨의 중복 체크를 통과한 경우에도 동시 요청 경쟁 조건에서 발생 가능.
     * DataIntegrityViolationException → 409 Conflict 반환.
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleDataIntegrityViolation(
            DataIntegrityViolationException e, HttpServletRequest req) {
        log.warn("[Data Integrity Violation] method={}, uri={}, message={}",
                req.getMethod(), req.getRequestURI(), e.getMessage());
        return ResponseEntity
                .status(CommonErrorCode.CONFLICT.getStatus())
                .body(ApiResponse.error(CommonErrorCode.CONFLICT));
    }

    /**
     * 낙관적 락 충돌 예외 처리 (동시 수정 충돌)
     * 두 트랜잭션이 같은 엔티티를 동시에 수정하려 할 때 발생. 409 Conflict 반환.
     */
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Void>> handleOptimisticLock(ObjectOptimisticLockingFailureException e, HttpServletRequest req) {
        log.warn("[Optimistic Lock] method={}, uri={}, entity={}",
                req.getMethod(), req.getRequestURI(), e.getPersistentClassName());
        return ResponseEntity
                .status(CommonErrorCode.CONFLICT.getStatus())
                .body(ApiResponse.error(CommonErrorCode.CONFLICT));
    }

    /**
     * 서버 내부에서 정의되지 않은 예외 처리
     * 처리되지 못하고 도달한 런타임 예외들을 잡아 500 에러로 변환하며, 추적을 위해 스택트레이스를 로깅한다.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest req) {

        log.error("[Unexpected Error] method={}, uri={}, message={}",
                req.getMethod(), req.getRequestURI(), e.getMessage(), e);

        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(INTERNAL_SERVER_ERROR));
    }
}
