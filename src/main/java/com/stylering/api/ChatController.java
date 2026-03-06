package com.stylering.api;

import com.stylering.api.dto.ChatCtaResponse;
import com.stylering.api.dto.ChatMessageResponse;
import com.stylering.api.dto.ChatSessionSummaryResponse;
import com.stylering.api.dto.CreateChatSessionRequest;
import com.stylering.api.dto.CreateChatSessionResponse;
import com.stylering.api.dto.PostChatMessageRequest;
import com.stylering.api.dto.PostChatMessageResponse;
import com.stylering.api.dto.RecommendationItemResponse;
import com.stylering.catalog.ShoppingLinkResolver;
import com.stylering.chat.ChatService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/chat")
public class ChatController {

    private final ChatService chatService;
    private final ShoppingLinkResolver shoppingLinkResolver;

    public ChatController(ChatService chatService, ShoppingLinkResolver shoppingLinkResolver) {
        this.chatService = chatService;
        this.shoppingLinkResolver = shoppingLinkResolver;
    }

    @GetMapping("/sessions")
    public List<ChatSessionSummaryResponse> listSessions(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        String firebaseUid = String.valueOf(authentication.getPrincipal());
        return chatService.listSessions(firebaseUid, PageRequest.of(page, size)).stream()
                .map(s -> new ChatSessionSummaryResponse(s.getId(), s.getStatus().name(), s.getCreatedAt(), s.getUpdatedAt()))
                .toList();
    }

    @GetMapping("/sessions/{id}/messages")
    public List<ChatMessageResponse> getSessionMessages(
            Authentication authentication,
            @PathVariable Long id,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size
    ) {
        String firebaseUid = String.valueOf(authentication.getPrincipal());
        return chatService.getSessionMessages(firebaseUid, id, PageRequest.of(page, size)).stream()
                .map(m -> new ChatMessageResponse(m.getId(), m.getRole().name().toLowerCase(), m.getContent(), m.getCreatedAt()))
                .toList();
    }

    @PostMapping("/sessions")
    public CreateChatSessionResponse createSession(
            Authentication authentication,
            @RequestBody(required = false) CreateChatSessionRequest request
    ) {
        String firebaseUid = String.valueOf(authentication.getPrincipal());
        Long sessionId = chatService.createSession(firebaseUid, request);
        return new CreateChatSessionResponse(sessionId);
    }

    @PostMapping("/messages")
    public PostChatMessageResponse postMessage(
            Authentication authentication,
            @Valid @RequestBody PostChatMessageRequest request
    ) {
        String firebaseUid = String.valueOf(authentication.getPrincipal());
        ChatService.AssistantReply reply =
                chatService.postUserMessage(firebaseUid, request.sessionId(), request.message());

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
                        pick.reason(),
                        pick.item().getImageUrl(),
                        shoppingLinkResolver.resolve(pick.item())
                ))
                .toList();
    }
}
