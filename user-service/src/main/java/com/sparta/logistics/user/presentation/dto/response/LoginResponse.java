package com.sparta.logistics.user.presentation.dto.response;

import com.sparta.logistics.user.domain.model.enums.UserRole;
import com.sparta.logistics.user.domain.model.enums.UserStatus;

public record LoginResponse(
        String accessToken, // 토큰

        String username,

        UserRole role,

        UserStatus status
) {
}
