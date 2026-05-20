package com.sparta.logistics.user.application.dto.request;

import com.sparta.logistics.user.domain.model.entity.UserEntity;
import com.sparta.logistics.user.domain.model.enums.UserRole;
import lombok.Builder;

import java.util.UUID;

@Builder
public record SignupCommand(
        String username,
        String password,
        String name,
        String email,
        String slackId,
        UserRole role,
        UUID hubId,
        UUID companyId

) {

    public UserEntity toEntity(String encodedPassword) {
        return UserEntity.builder()
                .username(username)
                .password(encodedPassword)
                .name(name)
                .email(email)
                .slackId(slackId)
                .role(role)
                .hubId(hubId)
                .companyId(companyId)
                .build();

    }
}
