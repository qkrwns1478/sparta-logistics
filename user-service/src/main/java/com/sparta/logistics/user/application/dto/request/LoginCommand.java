package com.sparta.logistics.user.application.dto.request;

import lombok.Builder;

@Builder
public record LoginCommand(
        String username,
        String password
) {

}
