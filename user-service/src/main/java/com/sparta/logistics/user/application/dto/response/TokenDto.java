package com.sparta.logistics.user.application.dto.response;

public record TokenDto(
        UserResult userResult,
        String accessToken,
        String refreshToken
) {
}
