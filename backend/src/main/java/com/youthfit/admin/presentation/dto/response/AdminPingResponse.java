package com.youthfit.admin.presentation.dto.response;

import java.time.LocalDateTime;

public record AdminPingResponse(
        String message,
        LocalDateTime serverTime
) {
    public static AdminPingResponse pong() {
        return new AdminPingResponse("pong", LocalDateTime.now());
    }
}
