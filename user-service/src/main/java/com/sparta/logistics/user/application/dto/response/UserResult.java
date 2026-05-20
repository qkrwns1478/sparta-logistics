package com.sparta.logistics.user.application.dto.response;

import com.sparta.logistics.user.domain.model.entity.UserEntity;
import com.sparta.logistics.user.domain.model.enums.UserRole;
import com.sparta.logistics.user.domain.model.enums.UserStatus;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.UUID;

@Builder
public record UserResult(
        UUID userId,
        String username,
        String name,
        UserRole role,
        UserStatus status,
        LocalDateTime createdAt
) {
    public static UserResult from(UserEntity user){
        return UserResult.builder()
                .userId(user.getId())
                .username(user.getUsername())
                .name(user.getName())
                .role(user.getRole())
                .status(user.getStatus())
                .createdAt(user.getCreatedAt())
                .build();
    }
}
