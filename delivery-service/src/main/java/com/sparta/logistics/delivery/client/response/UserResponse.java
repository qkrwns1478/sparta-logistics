package com.sparta.logistics.delivery.client.response;

import java.util.UUID;

// user-service 팀과 실제 응답 필드 구조 협의 후 수정 필요
public record UserResponse(
        UUID userId,
        String slackId
) {}
