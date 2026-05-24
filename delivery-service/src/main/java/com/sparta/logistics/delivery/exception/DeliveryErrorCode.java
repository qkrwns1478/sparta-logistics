package com.sparta.logistics.delivery.exception;

import com.sparta.logistics.common.exception.ErrorCode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
@AllArgsConstructor
public enum DeliveryErrorCode implements ErrorCode {

    DELIVERY_NOT_FOUND(HttpStatus.NOT_FOUND, "DELIVERY_404", "배송 정보를 찾을 수 없습니다."),
    DELIVERY_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "DELIVERY_001", "이미 삭제된 배송입니다."),
    INVALID_STATUS_TRANSITION(HttpStatus.BAD_REQUEST, "DELIVERY_002", "허용되지 않는 배송 상태 전이입니다."),

    MANAGER_NOT_FOUND(HttpStatus.NOT_FOUND, "DELIVERY_MGR_404", "배송담당자를 찾을 수 없습니다."),
    MANAGER_ALREADY_DELETED(HttpStatus.BAD_REQUEST, "DELIVERY_MGR_001", "이미 삭제된 배송담당자입니다."),
    MANAGER_IN_DELIVERY(HttpStatus.BAD_REQUEST, "DELIVERY_MGR_002", "배송 중인 담당자는 삭제할 수 없습니다."),
    MANAGER_ALREADY_EXISTS(HttpStatus.CONFLICT, "DELIVERY_MGR_409", "이미 등록된 배송담당자입니다."),

    ROUTE_NOT_FOUND(HttpStatus.NOT_FOUND, "DELIVERY_ROUTE_404", "배송경로를 찾을 수 없습니다."),

    HUB_NOT_FOUND(HttpStatus.BAD_REQUEST, "DELIVERY_HUB_001", "존재하지 않는 허브입니다."),
    HUB_SERVICE_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "DELIVERY_HUB_503", "Hub Service를 현재 사용할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
