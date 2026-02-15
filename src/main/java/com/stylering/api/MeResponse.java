package com.stylering.api;

import java.time.Instant;

public record MeResponse(
        String firebaseUid,
        Instant createdAt,
        Instant lastLoginAt
) {
}
