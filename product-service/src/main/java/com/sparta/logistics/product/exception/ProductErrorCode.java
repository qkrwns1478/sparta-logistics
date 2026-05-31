package com.sparta.logistics.product.exception;

import com.sparta.logistics.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum ProductErrorCode implements ErrorCode {

    // 400 Bad Request (내부 도메인)
    PRODUCT_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "PRODUCT_001", "이미 삭제된 상품입니다."),
    INVALID_PRODUCT_STATUS(HttpStatus.BAD_REQUEST, "PRODUCT_002", "유효하지 않은 상품 상태입니다."),
    COMPANY_HUB_MISMATCH(HttpStatus.BAD_REQUEST, "PRODUCT_003", "업체와 허브 정보가 일치하지 않습니다."),

    // 403 Forbidden (내부 권한)
    PRODUCT_ACCESS_DENIED(HttpStatus.FORBIDDEN, "PRODUCT_403", "해당 상품에 대한 접근 권한이 없습니다."),

    // 404 Not Found (내부 도메인)
    PRODUCT_NOT_FOUND(HttpStatus.NOT_FOUND, "PRODUCT_404", "상품을 찾을 수 없습니다."),

    // 404 Not Found (외부 의존: Company)
    EXTERNAL_COMPANY_NOT_FOUND(HttpStatus.NOT_FOUND, "EXTERNAL_404", "업체 정보를 찾을 수 없습니다."),

    // 503 Service Unavailable (외부 의존 장애)
    EXTERNAL_COMPANY_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "EXTERNAL_503", "Company 서비스에 접근할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
