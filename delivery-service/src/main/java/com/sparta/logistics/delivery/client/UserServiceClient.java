package com.sparta.logistics.delivery.client;

import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.delivery.client.response.UserResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

// user-service 팀과 실제 엔드포인트 경로 및 응답 구조 협의 필요
@FeignClient(name = "user-service")
public interface UserServiceClient {

    @GetMapping("/api/v1/users/{userId}")
    ApiResponse<UserResponse> getUser(@PathVariable UUID userId);
}
