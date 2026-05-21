package com.sparta.logistics.hub.exception;

import com.sparta.logistics.common.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum HubStockErrorCode implements ErrorCode {

    HUB_STOCK_ALREADY_EXISTS(HttpStatus.CONFLICT, "HUB_STOCK_ALREADY_EXISTS", "이미 존재하는 허브 재고입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
