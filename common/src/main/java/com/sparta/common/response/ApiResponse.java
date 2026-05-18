package com.sparta.common.response;

import com.sparta.common.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        int status,
        String erroCode,
        String message,
        T data
) {

    // 성공
    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true ,200,null, "OK",data);
    }

    // 커스텀 메세지 포함 성공
    public static <T> ApiResponse<T> success(String message, T data) {
        return new ApiResponse<>(true, 200, null, message, data);
    }

    // 성공(바디없음)
    public static <T> ApiResponse<T> noContent() {
        return new ApiResponse<>(true,200, null,"OK",null);
    }

    // 에러
    public static <T> ApiResponse<T> error(ErrorCode errorCode) {
        return new ApiResponse<>(false,  errorCode.getStatus().value(), errorCode.getCode(),errorCode.getMessage(),null);
    }

    // 커스텀 메세지 포함 에러
    public static <T> ApiResponse<T> error(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false,  errorCode.getStatus().value(), errorCode.getCode(), message, null);
    }
}
