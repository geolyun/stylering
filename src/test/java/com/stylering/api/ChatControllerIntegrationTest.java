package com.stylering.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stylering.auth.FirebaseTokenVerifier;
import com.stylering.auth.TokenVerificationException;
import com.stylering.auth.VerifiedFirebaseToken;
import com.stylering.chat.ChatMessage;
import com.stylering.chat.ChatMessageRepository;
import com.stylering.chat.ChatMessageRole;
import com.stylering.chat.ChatSession;
import com.stylering.chat.ChatSessionRepository;
import com.stylering.chat.ChatSessionStatus;
import com.stylering.llm.LlmClientException;
import com.stylering.llm.QuestionLlmClient;
import com.stylering.ratelimit.UserRateLimiter;
import com.stylering.user.UserAccountRepository;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(ChatControllerIntegrationTest.TestBeans.class)
class ChatControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private StubFirebaseTokenVerifier firebaseTokenVerifier;

    @Autowired
    private StubQuestionLlmClient stubQuestionLlmClient;

    @Autowired
    private StubUserRateLimiter stubUserRateLimiter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        firebaseTokenVerifier.clear();
        stubQuestionLlmClient.clear();
        stubUserRateLimiter.clear();
        chatMessageRepository.deleteAll();
        chatSessionRepository.deleteAll();
        userAccountRepository.deleteAll();
    }

    @Test
    void createSessionSuccess() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");

        mockMvc.perform(post("/api/v1/chat/sessions")
                        .header("Authorization", "Bearer token-user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").isNumber());

        Long sessionId = findSingleSessionIdByUid("firebase-user-a");
        ChatSession session = chatSessionRepository.findById(sessionId).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals(ChatSessionStatus.OPEN, session.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(session.getCreatedAt());
        org.junit.jupiter.api.Assertions.assertNotNull(session.getUpdatedAt());
    }

    @Test
    void postMessageToOwnSessionSuccessStoresTwoMessages() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");
        stubQuestionLlmClient.enqueue("어떤 핏을 가장 선호해요?");
        Long sessionId = createSession("token-user-a");

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", "Bearer token-user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"sessionId":%d,"content":"hello"}
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.assistantContent").value("어떤 핏을 가장 선호해요?"))
                .andExpect(jsonPath("$.userMessageId").isNumber())
                .andExpect(jsonPath("$.assistantMessageId").isNumber());

        List<ChatMessage> messages = chatMessageRepository.findBySession_IdOrderByIdAsc(sessionId);
        org.junit.jupiter.api.Assertions.assertEquals(2, messages.size());
        org.junit.jupiter.api.Assertions.assertEquals(ChatMessageRole.USER, messages.get(0).getRole());
        org.junit.jupiter.api.Assertions.assertEquals("hello", messages.get(0).getContent());
        org.junit.jupiter.api.Assertions.assertEquals(ChatMessageRole.ASSISTANT, messages.get(1).getRole());
        org.junit.jupiter.api.Assertions.assertEquals("어떤 핏을 가장 선호해요?", messages.get(1).getContent());
    }

    @Test
    void postMessageWhenLlmFailsReturnsFallbackQuestion() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");
        stubQuestionLlmClient.setShouldFail(true);
        Long sessionId = createSession("token-user-a");

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", "Bearer token-user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":%d,"content":"hello"}
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assistantContent").value("요즘 가장 자주 입는 스타일을 한 가지로 말해줄래요?"));
    }

    @Test
    void postMessageWithUnknownSessionReturnsNotFound() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", "Bearer token-user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":999999,"content":"hello"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("CHAT_SESSION_NOT_FOUND"));
    }

    @Test
    void postMessageToOtherUsersSessionReturnsForbidden() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");
        firebaseTokenVerifier.allow("token-user-b", "firebase-user-b");
        Long sessionId = createSession("token-user-a");

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", "Bearer token-user-b")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":%d,"content":"intrude"}
                                """.formatted(sessionId)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("CHAT_SESSION_FORBIDDEN"));
    }

    @Test
    void postMessageWithInvalidContentReturnsBadRequest() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");
        Long sessionId = createSession("token-user-a");

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", "Bearer token-user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":%d,"content":"   "}
                                """.formatted(sessionId)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        String tooLong = "a".repeat(2001);
        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", "Bearer token-user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":%d,"content":"%s"}
                                """.formatted(sessionId, tooLong)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void postMessageWhenRateLimitExceededReturnsTooManyRequests() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");
        stubQuestionLlmClient.enqueue("첫 번째 질문입니다?");
        stubUserRateLimiter.setMaxRequestsPerMinute(1);
        Long sessionId = createSession("token-user-a");

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", "Bearer token-user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":%d,"content":"first"}
                                """.formatted(sessionId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", "Bearer token-user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":%d,"content":"second"}
                                """.formatted(sessionId)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMIT_EXCEEDED"));
    }

    private Long createSession(String token) throws Exception {
        mockMvc.perform(post("/api/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        String uid = token.equals("token-user-a") ? "firebase-user-a" : "firebase-user-b";
        return findSingleSessionIdByUid(uid);
    }

    private Long findSingleSessionIdByUid(String firebaseUid) {
        List<ChatSession> sessions = chatSessionRepository.findByUser_FirebaseUid(firebaseUid);
        org.junit.jupiter.api.Assertions.assertEquals(1, sessions.size());
        return sessions.get(0).getId();
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        StubFirebaseTokenVerifier stubFirebaseTokenVerifier() {
            return new StubFirebaseTokenVerifier();
        }

        @Bean
        @Primary
        StubQuestionLlmClient stubQuestionLlmClient() {
            return new StubQuestionLlmClient();
        }

        @Bean
        @Primary
        StubUserRateLimiter stubUserRateLimiter() {
            return new StubUserRateLimiter();
        }
    }

    static class StubFirebaseTokenVerifier implements FirebaseTokenVerifier {

        private final Map<String, String> tokenToUid = new ConcurrentHashMap<>();
        private volatile boolean rejectAll;

        void allow(String token, String firebaseUid) {
            tokenToUid.put(token, firebaseUid);
        }

        void clear() {
            rejectAll = false;
            tokenToUid.clear();
        }

        @Override
        public VerifiedFirebaseToken verify(String idToken) {
            if (rejectAll) {
                throw new TokenVerificationException("invalid token");
            }

            String uid = tokenToUid.get(idToken);
            if (uid == null) {
                throw new TokenVerificationException("invalid token");
            }

            return new VerifiedFirebaseToken(uid);
        }
    }

    static class StubQuestionLlmClient implements QuestionLlmClient {

        private final ArrayDeque<String> queuedQuestions = new ArrayDeque<>();
        private volatile boolean shouldFail;

        void enqueue(String question) {
            queuedQuestions.addLast(question);
        }

        void setShouldFail(boolean shouldFail) {
            this.shouldFail = shouldFail;
        }

        void clear() {
            shouldFail = false;
            queuedQuestions.clear();
        }

        @Override
        public String generateNextQuestion(String systemPrompt, String userPrompt) {
            if (shouldFail) {
                throw new LlmClientException("forced failure");
            }
            if (!queuedQuestions.isEmpty()) {
                return queuedQuestions.removeFirst();
            }
            return "다음으로 어떤 색상을 선호하나요?";
        }
    }

    static class StubUserRateLimiter implements UserRateLimiter {

        private final Map<Long, ArrayDeque<Long>> userWindows = new ConcurrentHashMap<>();
        private volatile int maxRequestsPerMinute = 1000;

        void setMaxRequestsPerMinute(int maxRequestsPerMinute) {
            this.maxRequestsPerMinute = maxRequestsPerMinute;
        }

        void clear() {
            maxRequestsPerMinute = 1000;
            userWindows.clear();
        }

        @Override
        public void checkLimit(Long userId) {
            long now = Instant.now().toEpochMilli();
            long threshold = now - 60_000L;
            ArrayDeque<Long> window = userWindows.computeIfAbsent(userId, ignored -> new ArrayDeque<>());

            synchronized (window) {
                while (!window.isEmpty() && window.peekFirst() < threshold) {
                    window.removeFirst();
                }
                if (window.size() >= maxRequestsPerMinute) {
                    throw new com.stylering.common.error.ApiClientException(
                            org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                            "RATE_LIMIT_EXCEEDED",
                            "Rate limit exceeded"
                    );
                }
                window.addLast(now);
            }
        }
    }
}
