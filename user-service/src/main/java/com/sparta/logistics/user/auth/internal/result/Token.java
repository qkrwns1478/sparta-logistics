package com.sparta.logistics.user.auth.internal.result;

public record Token(
        UserResult userResult,
        String accessToken,
        String refreshToken
) {
}
