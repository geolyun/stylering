package com.stylering.chat;

import com.stylering.api.dto.PostRecommendationsRequest;
import com.stylering.common.error.ApiClientException;
import com.stylering.llm.NextQuestionGenerator;
import com.stylering.profile.PreferenceProfileService;
import com.stylering.recommend.RecommendationService;
import com.stylering.ratelimit.UserRateLimiter;
import com.stylering.user.UserAccount;
import com.stylering.user.UserAccountService;
import java.time.Instant;
import java.util.List;
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
    private final PreferenceProfileService preferenceProfileService;
    private final StopIntentDetector stopIntentDetector;
    private final RecommendationService recommendationService;

    public ChatService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            UserAccountService userAccountService,
            NextQuestionGenerator nextQuestionGenerator,
            UserRateLimiter userRateLimiter,
            PreferenceProfileService preferenceProfileService,
            StopIntentDetector stopIntentDetector,
            RecommendationService recommendationService
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userAccountService = userAccountService;
        this.nextQuestionGenerator = nextQuestionGenerator;
        this.userRateLimiter = userRateLimiter;
        this.preferenceProfileService = preferenceProfileService;
        this.stopIntentDetector = stopIntentDetector;
        this.recommendationService = recommendationService;
    }

    @Transactional
    public Long createSession(String firebaseUid) {
        UserAccount userAccount = userAccountService.getByFirebaseUid(firebaseUid);
        ChatSession session = new ChatSession(userAccount, ChatSessionStatus.INTERVIEWING);
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

        if (stopIntentDetector.isStopIntent(content)) {
            return handleStopIntent(firebaseUid, userAccount, session, userMessage);
        }

        NextQuestionGenerator.AssistantTurn turn = nextQuestionGenerator.generate(content);
        NextQuestionGenerator.NextAction nextAction = normalizeNonStopAction(turn.nextAction());
        session.setStatus(nextAction == NextQuestionGenerator.NextAction.SUGGEST_STOP
                ? ChatSessionStatus.READY_TO_RECOMMEND
                : ChatSessionStatus.INTERVIEWING);
        boolean profileUpdated = preferenceProfileService.tryRefreshProfile(userAccount, session);

        ChatMessage assistantMessage = chatMessageRepository.save(
                new ChatMessage(session, ChatMessageRole.ASSISTANT, turn.assistantContent())
        );
        session.touch(Instant.now());

        return new AssistantReply(
                session.getId(),
                userMessage.getId(),
                assistantMessage.getId(),
                turn.assistantContent(),
                nextAction,
                session.getStatus(),
                turn.ctaPrimary(),
                turn.ctaSecondary(),
                List.of(),
                profileUpdated
        );
    }

    private AssistantReply handleStopIntent(
            String firebaseUid,
            UserAccount userAccount,
            ChatSession session,
            ChatMessage userMessage
    ) {
        session.setStatus(ChatSessionStatus.STOPPED);
        boolean profileUpdated = preferenceProfileService.finalizeProfile(userAccount, session);
        RecommendationService.RecommendationResult recommendationResult =
                recommendationService.recommend(
                        firebaseUid,
                        new PostRecommendationsRequest(session.getId(), null, null)
                );
        session.setStatus(ChatSessionStatus.RECOMMENDED);
        String assistantContent = recommendationResult.nextQuestion();

        ChatMessage assistantMessage = chatMessageRepository.save(
                new ChatMessage(session, ChatMessageRole.ASSISTANT, assistantContent)
        );
        session.touch(Instant.now());

        return new AssistantReply(
                session.getId(),
                userMessage.getId(),
                assistantMessage.getId(),
                assistantContent,
                NextQuestionGenerator.NextAction.RECOMMEND,
                session.getStatus(),
                "추천 보기",
                "대화 계속",
                recommendationResult.recommendations(),
                profileUpdated
        );
    }

    private NextQuestionGenerator.NextAction normalizeNonStopAction(NextQuestionGenerator.NextAction action) {
        if (action == NextQuestionGenerator.NextAction.SUGGEST_STOP) {
            return NextQuestionGenerator.NextAction.SUGGEST_STOP;
        }
        return NextQuestionGenerator.NextAction.ASK;
    }

    public record AssistantReply(
            Long sessionId,
            Long userMessageId,
            Long assistantMessageId,
            String assistantContent,
            NextQuestionGenerator.NextAction nextAction,
            ChatSessionStatus sessionStatus,
            String ctaPrimary,
            String ctaSecondary,
            List<RecommendationService.PickedItem> recommendations,
            boolean profileUpdated
    ) {
    }
}
