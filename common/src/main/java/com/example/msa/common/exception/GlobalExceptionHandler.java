package com.example.msa.common.exception;

import com.example.msa.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.List;
import java.util.stream.Collectors;

import static com.example.msa.common.exception.CommonErrorCode.*;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    // 비즈니스 로직 예외 처리
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e, HttpServletRequest req) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("[BusinessException] method={}, uri={}, code={}, message={}",
                req.getMethod(), req.getRequestURI(), errorCode.getCode(), e.getMessage());
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ApiResponse.error(errorCode));
    }

    // @Valid 유효성 검증 실패
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

    // Enum, UUID 등 타입 변환 실패 (@RequestParam, @PathVariable)
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e, HttpServletRequest req) {
        String message = String.format("%s의 값이 올바르지 않음: %s", e.getName(), e.getValue());

        log.warn("[Type Mismatch] method={}, uri={}, message={}",
                req.getMethod(), req.getRequestURI(), message);

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(TYPE_MISMATCH, message));
    }

    // 필수 헤더 누락
    @ExceptionHandler(MissingRequestHeaderException.class)
    public ResponseEntity<ApiResponse<Void>> handleMissingHeader(MissingRequestHeaderException e, HttpServletRequest req) {
        log.warn("[Missing Header] method={}, uri={}, headerName={}",
                req.getMethod(), req.getRequestURI(), e.getHeaderName());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(MISSING_REQUEST_HEADER));
    }

    // JSON 파싱 실패 (잘못된 바디 형식)
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotReadable(HttpMessageNotReadableException e, HttpServletRequest req) {
        log.warn("[Invalid Body] method={}, uri={}, message={}",
                req.getMethod(), req.getRequestURI(), e.getMessage());

        return ResponseEntity.badRequest()
                .body(ApiResponse.error(INVALID_REQUEST_BODY));
    }

    // 그 외 예상치 못한 서버 에러 (500)
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e, HttpServletRequest req) {
        // 시스템 장애는 상세한 추적을 위해 stacktrace를 포함하도록 log.error 사용
        log.error("[Unexpected Error] method={}, uri={}, message={}",
                req.getMethod(), req.getRequestURI(), e.getMessage(), e);

        return ResponseEntity.internalServerError()
                .body(ApiResponse.error(INTERNAL_SERVER_ERROR));
    }
}
