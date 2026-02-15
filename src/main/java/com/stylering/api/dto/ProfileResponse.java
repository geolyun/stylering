package com.stylering.api.dto;

import java.time.Instant;

public record ProfileResponse(
        int version,
        String profileJson,
        String summary,
        Instant updatedAt
) {
}
