package com.stylering.api.dto;

public record PostChatMessageResponse(
        Long sessionId,
        Long userMessageId,
        Long assistantMessageId,
        String assistantContent
) {
}
