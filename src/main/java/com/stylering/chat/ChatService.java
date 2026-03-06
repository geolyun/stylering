package com.stylering.chat;

import com.stylering.api.dto.CreateChatSessionRequest;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Pageable;
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
    private final int maxInterviewTurns;

    public ChatService(
            ChatSessionRepository chatSessionRepository,
            ChatMessageRepository chatMessageRepository,
            UserAccountService userAccountService,
            NextQuestionGenerator nextQuestionGenerator,
            UserRateLimiter userRateLimiter,
            PreferenceProfileService preferenceProfileService,
            StopIntentDetector stopIntentDetector,
            RecommendationService recommendationService,
            @Value("${chat.max-interview-turns:8}") int maxInterviewTurns
    ) {
        this.chatSessionRepository = chatSessionRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.userAccountService = userAccountService;
        this.nextQuestionGenerator = nextQuestionGenerator;
        this.userRateLimiter = userRateLimiter;
        this.preferenceProfileService = preferenceProfileService;
        this.stopIntentDetector = stopIntentDetector;
        this.recommendationService = recommendationService;
        this.maxInterviewTurns = maxInterviewTurns;
    }

    @Transactional(readOnly = true)
    public List<ChatSession> listSessions(String firebaseUid, Pageable pageable) {
        return chatSessionRepository.findByUser_FirebaseUidOrderByUpdatedAtDesc(firebaseUid, pageable);
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getSessionMessages(String firebaseUid, Long sessionId, Pageable pageable) {
        UserAccount userAccount = userAccountService.getByFirebaseUid(firebaseUid);
        chatSessionRepository.findByIdAndUser_Id(sessionId, userAccount.getId())
                .orElseThrow(() -> new ApiClientException(
                        HttpStatus.NOT_FOUND,
                        "CHAT_SESSION_NOT_FOUND",
                        "Chat session not found"
                ));
        return chatMessageRepository.findBySession_IdOrderByIdAsc(sessionId, pageable);
    }

    @Transactional
    public Long createSession(String firebaseUid, CreateChatSessionRequest request) {
        UserAccount userAccount = userAccountService.getByFirebaseUid(firebaseUid);
        ChatSession session = new ChatSession(userAccount, ChatSessionStatus.INTERVIEWING);
        chatSessionRepository.save(session);
        if (hasStructuredInput(request)) {
            preferenceProfileService.seedFromStructured(userAccount, request);
        }
        return session.getId();
    }

    private boolean hasStructuredInput(CreateChatSessionRequest request) {
        if (request == null) return false;
        return request.budgetMin() != null
                || request.budgetMax() != null
                || (request.fitTop() != null && !request.fitTop().isBlank())
                || (request.fitPants() != null && !request.fitPants().isBlank())
                || (request.height() != null && !request.height().isBlank())
                || (request.occasions() != null && request.occasions().stream().anyMatch(s -> s != null && !s.isBlank()));
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

        ChatSessionStatus currentStatus = session.getStatus();
        if (currentStatus == ChatSessionStatus.STOPPED || currentStatus == ChatSessionStatus.RECOMMENDED) {
            throw new ApiClientException(
                    HttpStatus.CONFLICT,
                    "CHAT_SESSION_CLOSED",
                    "Chat session is already closed and cannot accept new messages"
            );
        }

        ChatMessage userMessage = chatMessageRepository.save(
                new ChatMessage(session, ChatMessageRole.USER, content)
        );

        if (stopIntentDetector.isStopIntent(content)) {
            return handleStopIntent(firebaseUid, userAccount, session, userMessage);
        }

        long userTurnCount = chatMessageRepository.countBySession_IdAndRole(session.getId(), ChatMessageRole.USER);
        String conversationHistory = buildConversationHistory(session.getId());

        // 최대 인터뷰 턴 수 초과 시 LLM 결과와 무관하게 강제 종료
        if (userTurnCount >= maxInterviewTurns) {
            return buildForcedSuggestStop(session, userMessage);
        }

        String followupQuestions = preferenceProfileService.getFollowupQuestionsText(userAccount);
        String confirmedAxes = preferenceProfileService.getConfirmedAxesText(userAccount);
        NextQuestionGenerator.AssistantTurn turn = nextQuestionGenerator.generate(content, conversationHistory, followupQuestions, confirmedAxes);
        NextQuestionGenerator.NextAction nextAction = normalizeNonStopAction(turn.nextAction());
        session.setStatus(nextAction == NextQuestionGenerator.NextAction.SUGGEST_STOP
                ? ChatSessionStatus.READY_TO_RECOMMEND
                : ChatSessionStatus.INTERVIEWING);
        boolean profileUpdated = preferenceProfileService.tryRefreshProfile(userAccount, session);

        ChatMessage assistantMessage = chatMessageRepository.save(
                new ChatMessage(session, ChatMessageRole.ASSISTANT, turn.assistantContent())
        );
        session.touch(Instant.now());

        String ctaPrimary = turn.ctaPrimary();
        String ctaSecondary = turn.ctaSecondary();
        if (nextAction == NextQuestionGenerator.NextAction.SUGGEST_STOP) {
            if (ctaPrimary == null || ctaPrimary.isBlank()) ctaPrimary = "추천 받기";
            if (ctaSecondary == null || ctaSecondary.isBlank()) ctaSecondary = "대화 계속";
        }

        return new AssistantReply(
                session.getId(),
                userMessage.getId(),
                assistantMessage.getId(),
                turn.assistantContent(),
                nextAction,
                session.getStatus(),
                ctaPrimary,
                ctaSecondary,
                List.of(),
                profileUpdated
        );
    }

    private AssistantReply buildForcedSuggestStop(ChatSession session, ChatMessage userMessage) {
        String content = "충분히 이야기를 나눴어요! 지금까지 말씀해주신 걸 바탕으로 추천을 드릴게요. 추천을 받아보실래요?";
        session.setStatus(ChatSessionStatus.READY_TO_RECOMMEND);
        ChatMessage assistantMessage = chatMessageRepository.save(
                new ChatMessage(session, ChatMessageRole.ASSISTANT, content)
        );
        session.touch(Instant.now());
        return new AssistantReply(
                session.getId(),
                userMessage.getId(),
                assistantMessage.getId(),
                content,
                NextQuestionGenerator.NextAction.SUGGEST_STOP,
                session.getStatus(),
                "추천 받기",
                "대화 계속",
                List.of(),
                false
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
                        new PostRecommendationsRequest(session.getId(), null, null, null)
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

    private String buildConversationHistory(Long sessionId) {
        List<ChatMessage> messages = chatMessageRepository.findBySession_IdOrderByIdAsc(sessionId);
        if (messages.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        for (ChatMessage m : messages) {
            sb.append(m.getRole().name()).append(": ").append(m.getContent()).append("\n");
        }
        return sb.toString().trim();
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
