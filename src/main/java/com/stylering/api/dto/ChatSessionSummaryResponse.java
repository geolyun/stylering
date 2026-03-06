package com.stylering.api.dto;

import java.time.Instant;

public record ChatSessionSummaryResponse(
        Long sessionId,
        String status,
        Instant createdAt,
        Instant updatedAt
) {
}
