package com.sparta.logistics.common.response;

import com.sparta.logistics.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        int status,
        String message,
        T data
) {

    // 성공
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true ,200, "OK",data);
    }

    // 커스텀 메세지 포함 성공
    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, 200, message, data);
    }

    // 성공(바디없음)
    public static <T> ApiResponse<T> noContent() {
        return new ApiResponse<>(true,200, "OK",null);
    }

    // 에러
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(false, 400,errorCode.getMessage(),null);
    }

    // 커스텀 메세지 포함 에러
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false,  400, message, null);
    }
}
