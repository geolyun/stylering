package com.stylering.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PostChatMessageRequest(
        @NotNull(message = "sessionId is required")
        Long sessionId,
        @NotBlank(message = "content must not be blank")
        @Size(max = 2000, message = "content must be at most 2000 characters")
        String content
) {
}
