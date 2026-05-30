package com.sparta.logistics.delivery.client;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.delivery.client.response.UserResponse;
import com.sparta.logistics.delivery.exception.DeliveryErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Slf4j
@Component
public class UserServiceClientFallback implements UserServiceClient {

    @Override
    public ApiResponse<UserResponse> getUser(UUID userId, String headerUserId, String role) {
        log.warn("[UserServiceClient Fallback] User Service 응답 없음. userId={}", userId);
        throw new BusinessException(DeliveryErrorCode.USER_SERVICE_UNAVAILABLE);
    }
}
