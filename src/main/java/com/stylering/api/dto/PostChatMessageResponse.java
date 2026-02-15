package com.stylering.api.dto;

import java.util.List;

public record PostChatMessageResponse(
        Long sessionId,
        Long userMessageId,
        Long assistantMessageId,
        String assistantContent,
        String nextAction,
        String sessionStatus,
        ChatCtaResponse cta,
        List<RecommendationItemResponse> recommendations,
        boolean profileUpdated
) {
}
