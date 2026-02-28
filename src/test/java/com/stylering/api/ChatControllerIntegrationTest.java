package com.stylering.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.nullValue;

import com.stylering.auth.FirebaseTokenVerifier;
import com.stylering.auth.TokenVerificationException;
import com.stylering.auth.VerifiedFirebaseToken;
import com.stylering.catalog.CatalogItem;
import com.stylering.catalog.CatalogItemRepository;
import com.stylering.catalog.CatalogItemType;
import com.stylering.chat.ChatMessage;
import com.stylering.chat.ChatMessageRepository;
import com.stylering.chat.ChatMessageRole;
import com.stylering.chat.ChatSession;
import com.stylering.chat.ChatSessionRepository;
import com.stylering.chat.ChatSessionStatus;
import com.stylering.llm.LlmClientException;
import com.stylering.llm.ProfileLlmClient;
import com.stylering.llm.QuestionLlmClient;
import com.stylering.llm.RecommendationLlmClient;
import com.stylering.profile.PreferenceProfileRepository;
import com.stylering.ratelimit.UserRateLimiter;
import com.stylering.recommend.RecommendationHistoryRepository;
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
    private PreferenceProfileRepository preferenceProfileRepository;

    @Autowired
    private RecommendationHistoryRepository recommendationHistoryRepository;

    @Autowired
    private CatalogItemRepository catalogItemRepository;

    @Autowired
    private StubFirebaseTokenVerifier firebaseTokenVerifier;

    @Autowired
    private StubQuestionLlmClient stubQuestionLlmClient;

    @Autowired
    private StubProfileLlmClient stubProfileLlmClient;

    @Autowired
    private StubRecommendationLlmClient stubRecommendationLlmClient;

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
        stubProfileLlmClient.clear();
        stubRecommendationLlmClient.clear();
        stubUserRateLimiter.clear();

        recommendationHistoryRepository.deleteAll();
        chatMessageRepository.deleteAll();
        chatSessionRepository.deleteAll();
        preferenceProfileRepository.deleteAll();
        catalogItemRepository.deleteAll();
        userAccountRepository.deleteAll();

        catalogItemRepository.save(new CatalogItem(
                CatalogItemType.SHOES,
                "shoe-alpha",
                "brand-a",
                "50000-120000",
                "[\"minimal\",\"black\"]",
                "UNISEX",
                "SS"
        ));
        catalogItemRepository.save(new CatalogItem(
                CatalogItemType.TOP,
                "top-alpha",
                "brand-b",
                "40000-90000",
                "[\"minimal\",\"daily\"]",
                "UNISEX",
                "SS"
        ));
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
        org.junit.jupiter.api.Assertions.assertEquals(ChatSessionStatus.INTERVIEWING, session.getStatus());
        org.junit.jupiter.api.Assertions.assertNotNull(session.getCreatedAt());
        org.junit.jupiter.api.Assertions.assertNotNull(session.getUpdatedAt());
    }

    @Test
    void stopIntentReturnsRecommendations() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");
        stubProfileLlmClient.enqueue("""
                {"style_archetypes":["minimal"],"colors":{"like":["black"],"avoid":[]},"fit":{"top":"regular","pants":"wide"},"brands":{"like":[],"avoid":[]},"budget":{"min":50000,"max":200000},"context":{"ageRange":"20s","occasion":["daily"]},"constraints":[],"confidence":0.8,"summary":"final"}
                """);
        stubRecommendationLlmClient.setResponse("""
                {"recommendations":[{"category":"shoes","item_id":999999,"reason":"bad"}],"alternatives":[],"next_question":"done"}
                """);

        Long sessionId = createSession("token-user-a");

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", "Bearer token-user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":%d,"message":"finish"}
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAction").value("RECOMMEND"))
                .andExpect(jsonPath("$.sessionStatus").value("RECOMMENDED"))
                .andExpect(jsonPath("$.recommendations").isArray())
                .andExpect(jsonPath("$.recommendations[0].itemId").isNumber())
                .andExpect(jsonPath("$.recommendations[0].shopUrl").isNotEmpty());
    }

    @Test
    void readyToRecommendThenRecommendReturnsRecommendations() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");
        stubQuestionLlmClient.enqueue("""
                {"assistantContent":"Enough context, move to recommendation?","nextAction":"SUGGEST_STOP","cta":{"primary":"Get recommendation","secondary":"Ask more"}}
                """);
        stubProfileLlmClient.enqueue("""
                {"style_archetypes":["minimal"],"colors":{"like":["black"],"avoid":[]},"fit":{"top":"regular","pants":"wide"},"brands":{"like":[],"avoid":[]},"budget":{"min":50000,"max":200000},"context":{"ageRange":"20s","occasion":["daily"]},"constraints":[],"confidence":0.8,"summary":"final"}
                """);
        stubRecommendationLlmClient.setResponse("""
                {"recommendations":[{"category":"shoes","item_id":999999,"reason":"bad"}],"alternatives":[],"next_question":"done"}
                """);

        Long sessionId = createSession("token-user-a");

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", "Bearer token-user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":%d,"message":"I prefer minimal"}
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAction").value("SUGGEST_STOP"))
                .andExpect(jsonPath("$.sessionStatus").value("READY_TO_RECOMMEND"));

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", "Bearer token-user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":%d,"message":"finish"}
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAction").value("RECOMMEND"))
                .andExpect(jsonPath("$.sessionStatus").value("RECOMMENDED"))
                .andExpect(jsonPath("$.recommendations").isArray());
    }

    @Test
    void suggestStopResponseContainsCtaAndNoRecommendations() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");
        stubQuestionLlmClient.enqueue("""
                {"assistantContent":"Enough context, move to recommendation?","nextAction":"SUGGEST_STOP","cta":{"primary":"Get recommendation","secondary":"Ask more"}}
                """);
        Long sessionId = createSession("token-user-a");

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", "Bearer token-user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":%d,"message":"black is good"}
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAction").value("SUGGEST_STOP"))
                .andExpect(jsonPath("$.sessionStatus").value("READY_TO_RECOMMEND"))
                .andExpect(jsonPath("$.cta.primary").value("Get recommendation"))
                .andExpect(jsonPath("$.cta.secondary").value("Ask more"))
                .andExpect(jsonPath("$.recommendations").value(nullValue()));
    }

    @Test
    void llmFailureFallsBackToAskOnly() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");
        stubQuestionLlmClient.setShouldFail(true);
        Long sessionId = createSession("token-user-a");

        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", "Bearer token-user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":%d,"message":"hello"}
                                """.formatted(sessionId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nextAction").value("ASK"))
                .andExpect(jsonPath("$.sessionStatus").value("INTERVIEWING"))
                .andExpect(jsonPath("$.assistantContent").isNotEmpty())
                .andExpect(jsonPath("$.recommendations").value(nullValue()));

        List<ChatMessage> messages = chatMessageRepository.findBySession_IdOrderByIdAsc(sessionId);
        org.junit.jupiter.api.Assertions.assertEquals(2, messages.size());
        org.junit.jupiter.api.Assertions.assertEquals(ChatMessageRole.USER, messages.get(0).getRole());
        org.junit.jupiter.api.Assertions.assertEquals(ChatMessageRole.ASSISTANT, messages.get(1).getRole());
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
        StubProfileLlmClient stubProfileLlmClient() {
            return new StubProfileLlmClient();
        }

        @Bean
        @Primary
        StubRecommendationLlmClient stubRecommendationLlmClient() {
            return new StubRecommendationLlmClient();
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
            return "{" +
                    "\"assistantContent\":\"Which color do you usually prefer?\"," +
                    "\"nextAction\":\"ASK\"," +
                    "\"cta\":{\"primary\":\"continue\",\"secondary\":\"recommend\"}" +
                    "}";
        }
    }

    static class StubProfileLlmClient implements ProfileLlmClient {
        private final ArrayDeque<String> queued = new ArrayDeque<>();

        void enqueue(String value) {
            queued.addLast(value);
        }

        void clear() {
            queued.clear();
        }

        @Override
        public String generateProfileJson(String systemPrompt, String userPrompt) {
            if (!queued.isEmpty()) {
                return queued.removeFirst();
            }
            return "{" +
                    "\"style_archetypes\":[\"minimal\"]," +
                    "\"colors\":{\"like\":[\"black\"],\"avoid\":[]}," +
                    "\"fit\":{\"top\":\"regular\",\"pants\":\"wide\"}," +
                    "\"brands\":{\"like\":[],\"avoid\":[]}," +
                    "\"budget\":{\"min\":50000,\"max\":200000}," +
                    "\"context\":{\"ageRange\":\"20s\",\"occasion\":[\"daily\"]}," +
                    "\"constraints\":[]," +
                    "\"confidence\":0.8," +
                    "\"summary\":\"ok\"}";
        }
    }

    static class StubRecommendationLlmClient implements RecommendationLlmClient {
        private String response = "{\"recommendations\":[],\"alternatives\":[],\"next_question\":\"done\"}";

        void setResponse(String response) {
            this.response = response;
        }

        void clear() {
            response = "{\"recommendations\":[],\"alternatives\":[],\"next_question\":\"done\"}";
        }

        @Override
        public String pickFromCandidates(String systemPrompt, String userPrompt) {
            return response;
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


