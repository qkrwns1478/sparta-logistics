package com.sparta.logistics.user.presentation.dto.response;

import com.sparta.logistics.user.domain.model.enums.UserRole;
import com.sparta.logistics.user.domain.model.enums.UserStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record UserRespons(
        UUID userId,
        String username,
        String name,
        UserRole role,
        UserStatus status,
        LocalDateTime createdAt;
) {
}
