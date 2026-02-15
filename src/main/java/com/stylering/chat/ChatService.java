package com.stylering.chat;

import com.stylering.common.error.ApiClientException;
import com.stylering.llm.NextQuestionGenerator;
import com.stylering.ratelimit.UserRateLimiter;
import com.stylering.user.UserAccount;
import com.stylering.user.UserAccountService;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChatService {

    private final ChatSessionRepository chatSessionRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final UserAccountService userAccountService;
    private final NextQuestionGenerator nextQuestionGenerator;
    private final UserRateLimiter userRateLimiter;

    public ChatService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            UserAccountService userAccountService,
            NextQuestionGenerator nextQuestionGenerator,
            UserRateLimiter userRateLimiter
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userAccountService = userAccountService;
        this.nextQuestionGenerator = nextQuestionGenerator;
        this.userRateLimiter = userRateLimiter;
    }

    @Transactional
    public Long createSession(String firebaseUid) {
        UserAccount userAccount = userAccountService.getByFirebaseUid(firebaseUid);
        ChatSession session = new ChatSession(userAccount, ChatSessionStatus.OPEN);
        return chatSessionRepository.save(session).getId();
    }

    @Transactional
    public AssistantReply postUserMessage(String firebaseUid, Long sessionId, String content) {
        UserAccount userAccount = userAccountService.getByFirebaseUid(firebaseUid);
        userRateLimiter.checkLimit(userAccount.getId());

        ChatSession session = chatSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ApiClientException(
                        HttpStatus.NOT_FOUND,
                        "CHAT_SESSION_NOT_FOUND",
                        "Chat session not found"
                ));

        if (!session.getUser().getId().equals(userAccount.getId())) {
            throw new ApiClientException(HttpStatus.FORBIDDEN, "CHAT_SESSION_FORBIDDEN", "Forbidden chat session");
        }

        ChatMessage userMessage = chatMessageRepository.save(
                new ChatMessage(session, ChatMessageRole.USER, content)
        );

        String assistantContent = nextQuestionGenerator.generate(content);
        ChatMessage assistantMessage = chatMessageRepository.save(
                new ChatMessage(session, ChatMessageRole.ASSISTANT, assistantContent)
        );

        session.touch(Instant.now());

        return new AssistantReply(
                session.getId(),
                userMessage.getId(),
                assistantMessage.getId(),
                assistantContent
        );
    }

    public record AssistantReply(
            Long sessionId,
            Long userMessageId,
            Long assistantMessageId,
            String assistantContent
    ) {
    }
}
