package com.sparta.logistics.user.presentation.dto.response;

import com.sparta.logistics.user.application.dto.response.UserResult;
import com.sparta.logistics.user.domain.model.enums.UserRole;
import com.sparta.logistics.user.domain.model.enums.UserStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record UserResponse(
        UUID userId,
        String username,
        String name,
        UserRole role,
        UserStatus status,
        LocalDateTime createdAt
) {
    public static UserResponse from(UserResult userResult) {
        return UserResponse.builder()
                .userId(userResult.userId())
                .username(userResult.username())
                .name(userResult.name())
                .role(userResult.role())
                .status(userResult.status())
                .build();
    }
}
