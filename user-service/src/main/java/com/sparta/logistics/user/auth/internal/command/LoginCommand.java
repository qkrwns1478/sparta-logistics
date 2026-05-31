package com.sparta.logistics.user.auth.internal.command;

import lombok.Builder;

@Builder
public record LoginCommand(
        String username,
        String password
) {

}
