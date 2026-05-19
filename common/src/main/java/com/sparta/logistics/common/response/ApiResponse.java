package com.sparta.logistics.common.response;

import com.sparta.logistics.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 전역 API 공통 응답 규격 레코드
 * 모든 API 요청에 대해 일관된 JSON 반환 포맷을 보장한다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        int status,
        String message,
        T data
) {

    // 200 OK: 일반 성공 (데이터 포함)
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true ,200, "OK",data);
    }

    // 200 OK: 일반 성공 (커스텀 메시지 및 데이터 포함)
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, 200, message, data);
    }

    // 201 Created: 리소스 생성 성공 (데이터 포함)
    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(true, 201, "Created", data);
    }

    // 201 Created: 리소스 생성 성공 (커스텀 메시지 및 데이터 포함)
    public static <T> ApiResponse<T> created(String message, T data) {
        return new ApiResponse<>(true, 201, message, data);
    }

    // 200 OK: 성공 (반환 데이터 없음)
    public static <T> ApiResponse<T> noContent() {
        return new ApiResponse<>(true,200, "OK",null);
    }

    // 400 Bad Request: 에러 발생 (기본 에러 메시지)
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, 400,errorCode.getMessage(),null);
    }

    // 400 Bad Request: 에러 발생 (커스텀 에러 메시지)
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false,  400, message, null);
    }
}
