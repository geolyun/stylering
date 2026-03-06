package com.stylering.api.dto;

import java.time.Instant;

public record ChatMessageResponse(
        Long messageId,
        String role,
        String content,
        Instant createdAt
) {
}
