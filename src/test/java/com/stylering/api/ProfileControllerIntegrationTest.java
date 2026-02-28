package com.stylering.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stylering.auth.FirebaseTokenVerifier;
import com.stylering.auth.TokenVerificationException;
import com.stylering.auth.VerifiedFirebaseToken;
import com.stylering.chat.ChatMessageRepository;
import com.stylering.chat.ChatSession;
import com.stylering.chat.ChatSessionRepository;
import com.stylering.llm.LlmClientException;
import com.stylering.llm.ProfileLlmClient;
import com.stylering.llm.QuestionLlmClient;
import com.stylering.profile.PreferenceProfile;
import com.stylering.profile.PreferenceProfileRepository;
import com.stylering.ratelimit.UserRateLimiter;
import com.stylering.user.UserAccountRepository;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
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
@Import(ProfileControllerIntegrationTest.TestBeans.class)
class ProfileControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PreferenceProfileRepository preferenceProfileRepository;

    @Autowired
    private ChatSessionRepository chatSessionRepository;

    @Autowired
    private ChatMessageRepository chatMessageRepository;

    @Autowired
    private StubFirebaseTokenVerifier firebaseTokenVerifier;

    @Autowired
    private StubQuestionLlmClient stubQuestionLlmClient;

    @Autowired
    private StubProfileLlmClient stubProfileLlmClient;

    @Autowired
    private StubUserRateLimiter stubUserRateLimiter;

    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        stubQuestionLlmClient.clear();
        stubProfileLlmClient.clear();
        stubUserRateLimiter.clear();
        firebaseTokenVerifier.clear();
        preferenceProfileRepository.deleteAll();
        chatMessageRepository.deleteAll();
        chatSessionRepository.deleteAll();
        userAccountRepository.deleteAll();
    }

    @Test
    void profileSavedAndVersionIncreasesWhenLlmReturnsValidJson() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");
        stubProfileLlmClient.enqueue("""
                {"style_archetypes":["minimal"],"colors":{"like":["black"],"avoid":[]},"fit":{"top":"regular","pants":"wide"},"brands":{"like":[],"avoid":[]},"budget":{"min":50000,"max":150000},"context":{"ageRange":"20s","occasion":["campus"]},"constraints":[],"confidence":0.7,"summary":"first"}
                """);
        stubProfileLlmClient.enqueue("""
                {"style_archetypes":["street"],"colors":{"like":["navy"],"avoid":["neon"]},"fit":{"top":"oversized","pants":"wide"},"brands":{"like":["brand-a"],"avoid":[]},"budget":{"min":70000,"max":200000},"context":{"ageRange":"20s","occasion":["daily"]},"constraints":["no_leather"],"confidence":0.8,"summary":"second"}
                """);

        Long sessionId = createSession("token-user-a");
        postMessage("token-user-a", sessionId, "m1");
        postMessage("token-user-a", sessionId, "m2");
        postMessage("token-user-a", sessionId, "m3");
        postMessage("token-user-a", sessionId, "m4");
        postMessage("token-user-a", sessionId, "m5");

        Long userId = userAccountRepository.findByFirebaseUid("firebase-user-a").orElseThrow().getId();
        PreferenceProfile profile = preferenceProfileRepository.findByUser_Id(userId).orElseThrow();
        Assertions.assertEquals(1, profile.getVersion());
        Assertions.assertEquals("first", profile.getSummary());

        mockMvc.perform(get("/api/v1/profile")
                        .header("Authorization", "Bearer token-user-a"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.summary").value("first"));
    }

    @Test
    void profileRetrySucceedsAfterFirstJsonParsingFailure() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");
        stubProfileLlmClient.enqueue("this is not json");
        stubProfileLlmClient.enqueue("""
                {"style_archetypes":["minimal"],"colors":{"like":["black"],"avoid":[]},"fit":{"top":"regular","pants":"wide"},"brands":{"like":[],"avoid":[]},"budget":{"min":50000,"max":150000},"context":{"ageRange":"20s","occasion":["campus"]},"constraints":[],"confidence":0.6,"summary":"retry-success"}
                """);

        Long sessionId = createSession("token-user-a");
        postMessage("token-user-a", sessionId, "m1");
        postMessage("token-user-a", sessionId, "m2");
        postMessage("token-user-a", sessionId, "m3");

        Long userId = userAccountRepository.findByFirebaseUid("firebase-user-a").orElseThrow().getId();
        PreferenceProfile profile = preferenceProfileRepository.findByUser_Id(userId).orElseThrow();
        Assertions.assertEquals(1, profile.getVersion());
        Assertions.assertEquals("retry-success", profile.getSummary());
        Assertions.assertEquals(2, stubProfileLlmClient.getCallCount());
    }

    @SuppressWarnings("unchecked")
    @Test
    void followupQuestionsIncludedInProfileJsonWhenLlmReturnsIt() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");
        stubProfileLlmClient.enqueue("""
                {"style_archetypes":["minimal"],"colors":{"like":["black"],"avoid":[]},"fit":{"top":"regular","pants":"wide"},"brands":{"like":[],"avoid":[]},"budget":{"min":50000,"max":150000},"context":{"ageRange":"20s","occasion":["campus"]},"constraints":[],"confidence":0.7,"followup_questions":["선호하는 신발 브랜드가 있나요?","주로 어떤 계절 옷을 사나요?"],"summary":"with-followup"}
                """);

        Long sessionId = createSession("token-user-a");
        postMessage("token-user-a", sessionId, "m1");
        postMessage("token-user-a", sessionId, "m2");
        postMessage("token-user-a", sessionId, "m3");

        Long userId = userAccountRepository.findByFirebaseUid("firebase-user-a").orElseThrow().getId();
        PreferenceProfile profile = preferenceProfileRepository.findByUser_Id(userId).orElseThrow();
        Map<String, Object> parsed = jsonParser.parseMap(profile.getProfileJson());

        Object followup = parsed.get("followup_questions");
        Assertions.assertNotNull(followup, "followup_questions must be present in profileJson");
        Assertions.assertInstanceOf(List.class, followup);
        List<String> questions = (List<String>) followup;
        Assertions.assertEquals(2, questions.size());
        Assertions.assertEquals("선호하는 신발 브랜드가 있나요?", questions.get(0));
    }

    @SuppressWarnings("unchecked")
    @Test
    void followupQuestionsDefaultsToEmptyListWhenLlmOmitsIt() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");
        stubProfileLlmClient.enqueue("""
                {"style_archetypes":["casual"],"colors":{"like":[],"avoid":[]},"fit":{"top":"regular","pants":"slim"},"brands":{"like":[],"avoid":[]},"budget":{"min":30000,"max":100000},"context":{"ageRange":"30s","occasion":["daily"]},"constraints":[],"confidence":0.5,"summary":"no-followup"}
                """);

        Long sessionId = createSession("token-user-a");
        postMessage("token-user-a", sessionId, "m1");
        postMessage("token-user-a", sessionId, "m2");
        postMessage("token-user-a", sessionId, "m3");

        Long userId = userAccountRepository.findByFirebaseUid("firebase-user-a").orElseThrow().getId();
        PreferenceProfile profile = preferenceProfileRepository.findByUser_Id(userId).orElseThrow();
        Map<String, Object> parsed = jsonParser.parseMap(profile.getProfileJson());

        Object followup = parsed.get("followup_questions");
        Assertions.assertNotNull(followup, "followup_questions must default to empty list");
        Assertions.assertInstanceOf(List.class, followup);
        Assertions.assertTrue(((List<String>) followup).isEmpty());
    }

    @Test
    void fallbackProfileSavedWhenRetryAlsoFails() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");
        stubProfileLlmClient.enqueue("bad-1");
        stubProfileLlmClient.enqueue("bad-2");

        Long sessionId = createSession("token-user-a");
        postMessage("token-user-a", sessionId, "m1");
        postMessage("token-user-a", sessionId, "m2");
        postMessage("token-user-a", sessionId, "m3");

        Long userId = userAccountRepository.findByFirebaseUid("firebase-user-a").orElseThrow().getId();
        PreferenceProfile profile = preferenceProfileRepository.findByUser_Id(userId).orElseThrow();
        Assertions.assertEquals(1, profile.getVersion());
        Assertions.assertEquals("Fallback profile generated due to profile parsing failure.", profile.getSummary());

        Map<String, Object> parsed = jsonParser.parseMap(profile.getProfileJson());
        Object confidence = parsed.get("confidence");
        Assertions.assertTrue(confidence instanceof Number);
        Assertions.assertEquals(0.1d, ((Number) confidence).doubleValue());

        Object followup = parsed.get("followup_questions");
        Assertions.assertNotNull(followup, "fallback must include followup_questions");
        Assertions.assertInstanceOf(List.class, followup);
        Assertions.assertTrue(((List<?>) followup).isEmpty());
    }

    private Long createSession(String token) throws Exception {
        mockMvc.perform(post("/api/v1/chat/sessions")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());

        String uid = token.equals("token-user-a") ? "firebase-user-a" : "firebase-user-b";
        List<ChatSession> sessions = chatSessionRepository.findByUser_FirebaseUid(uid);
        Assertions.assertEquals(1, sessions.size());
        return sessions.get(0).getId();
    }

    private void postMessage(String token, Long sessionId, String content) throws Exception {
        mockMvc.perform(post("/api/v1/chat/messages")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"sessionId":%d,"message":"%s"}
                                """.formatted(sessionId, content)))
                .andExpect(status().isOk());
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
        StubUserRateLimiter stubUserRateLimiter() {
            return new StubUserRateLimiter();
        }
    }

    static class StubFirebaseTokenVerifier implements FirebaseTokenVerifier {

        private final Map<String, String> tokenToUid = new ConcurrentHashMap<>();

        void allow(String token, String firebaseUid) {
            tokenToUid.put(token, firebaseUid);
        }

        void clear() {
            tokenToUid.clear();
        }

        @Override
        public VerifiedFirebaseToken verify(String idToken) {
            String uid = tokenToUid.get(idToken);
            if (uid == null) {
                throw new TokenVerificationException("invalid token");
            }
            return new VerifiedFirebaseToken(uid);
        }
    }

    static class StubQuestionLlmClient implements QuestionLlmClient {
        void clear() {
        }

        @Override
        public String generateNextQuestion(String systemPrompt, String userPrompt) {
            return "next-question?";
        }
    }

    static class StubProfileLlmClient implements ProfileLlmClient {

        private final ArrayDeque<String> queue = new ArrayDeque<>();
        private int callCount;

        void enqueue(String response) {
            queue.addLast(response);
        }

        int getCallCount() {
            return callCount;
        }

        void clear() {
            queue.clear();
            callCount = 0;
        }

        @Override
        public String generateProfileJson(String systemPrompt, String userPrompt) {
            callCount++;
            if (!queue.isEmpty()) {
                return queue.removeFirst();
            }
            throw new LlmClientException("missing stub profile response");
        }
    }

    static class StubUserRateLimiter implements UserRateLimiter {
        void clear() {
        }

        @Override
        public void checkLimit(Long userId) {
        }
    }
}
