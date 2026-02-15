package com.stylering.api;

import com.stylering.api.dto.ChatCtaResponse;
import com.stylering.api.dto.CreateChatSessionResponse;
import com.stylering.api.dto.PostChatMessageRequest;
import com.stylering.api.dto.PostChatMessageResponse;
import com.stylering.api.dto.RecommendationItemResponse;
import com.stylering.chat.ChatService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    @PostMapping("/sessions")
    public CreateChatSessionResponse createSession(Authentication authentication) {
        String firebaseUid = String.valueOf(authentication.getPrincipal());
        Long sessionId = chatService.createSession(firebaseUid);
        return new CreateChatSessionResponse(sessionId);
    }

    @PostMapping("/messages")
    public PostChatMessageResponse postMessage(
            Authentication authentication,
            @Valid @RequestBody PostChatMessageRequest request
    ) {
        String firebaseUid = String.valueOf(authentication.getPrincipal());
        ChatService.AssistantReply reply =
                chatService.postUserMessage(firebaseUid, request.sessionId(), request.content());

        return new PostChatMessageResponse(
                reply.sessionId(),
                reply.userMessageId(),
                reply.assistantMessageId(),
                reply.assistantContent(),
                reply.nextAction().name(),
                reply.sessionStatus().name(),
                new ChatCtaResponse(reply.ctaPrimary(), reply.ctaSecondary()),
                toRecommendationItems(reply),
                reply.profileUpdated()
        );
    }

    private List<RecommendationItemResponse> toRecommendationItems(ChatService.AssistantReply reply) {
        if (reply.recommendations() == null || reply.recommendations().isEmpty()) {
            return null;
        }
        return reply.recommendations().stream()
                .map(pick -> new RecommendationItemResponse(
                        pick.item().getId(),
                        pick.item().getType().name().toLowerCase(),
                        pick.item().getName(),
                        pick.item().getBrand(),
                        pick.item().getPriceRange(),
                        pick.reason()
                ))
                .toList();
    }
}
