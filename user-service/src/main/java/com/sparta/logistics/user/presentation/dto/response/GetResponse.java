package com.sparta.logistics.user.presentation.dto.response;

import com.sparta.logistics.user.domain.model.enums.UserStatus;

import javax.management.relation.Role;
import java.time.LocalDateTime;
import java.util.UUID;

public record GetResponse( // 조회 응답
        UUID userId,
        String username,
        String name,
        String email,
        String slackId,
        Role role,
        UserStatus status,
        UUID hubId,
        UUID companyId,
        LocalDateTime lastLoginAt,
        LocalDateTime createdAt

) {
}
