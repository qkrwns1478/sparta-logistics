package com.sparta.logistics.user.auth.dto.response;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.user.auth.internal.result.UserResult;
import lombok.Builder;

import java.util.UUID;

@Builder
public record LoginResponse( // 로그인 응답
        String accessToken,

        UUID userId,

        String username,

        Role role
) {

    public static LoginResponse from(String accessToken, UserResult userResult) {
        return LoginResponse.builder()
                .accessToken(accessToken)
                .userId(userResult.userId())
                .username(userResult.username())
                .role(userResult.role())
                .build();
    }
}
