package com.stylering.api;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.stylering.auth.FirebaseTokenVerifier;
import com.stylering.auth.TokenVerificationException;
import com.stylering.auth.VerifiedFirebaseToken;
import com.stylering.catalog.CatalogItem;
import com.stylering.catalog.CatalogItemRepository;
import com.stylering.catalog.CatalogItemType;
import com.stylering.llm.RecommendationLlmClient;
import com.stylering.profile.PreferenceProfile;
import com.stylering.profile.PreferenceProfileRepository;
import com.stylering.recommend.RecommendationHistory;
import com.stylering.recommend.RecommendationHistoryRepository;
import com.stylering.user.UserAccount;
import com.stylering.user.UserAccountRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Assertions;
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
@Import(RecommendationControllerIntegrationTest.TestBeans.class)
class RecommendationControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private PreferenceProfileRepository preferenceProfileRepository;

    @Autowired
    private CatalogItemRepository catalogItemRepository;

    @Autowired
    private RecommendationHistoryRepository recommendationHistoryRepository;

    @Autowired
    private StubFirebaseTokenVerifier firebaseTokenVerifier;

    @Autowired
    private StubRecommendationLlmClient stubRecommendationLlmClient;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();

        stubRecommendationLlmClient.clear();
        firebaseTokenVerifier.clear();
        recommendationHistoryRepository.deleteAll();
        preferenceProfileRepository.deleteAll();
        catalogItemRepository.deleteAll();
        userAccountRepository.deleteAll();
    }

    @Test
    void llmReturningOutOfCandidateIdTriggersFallback() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");
        CatalogItem first = catalogItemRepository.save(new CatalogItem(
                CatalogItemType.SHOES, "shoe-1", "brand-a", "50000-120000", "[\"minimal\",\"black\"]", "UNISEX", "SS"
        ));
        catalogItemRepository.save(new CatalogItem(
                CatalogItemType.SHOES, "shoe-2", "brand-b", "60000-130000", "[\"street\",\"navy\"]", "UNISEX", "FW"
        ));
        createProfile("token-user-a", "firebase-user-a");
        stubRecommendationLlmClient.setResponse("""
                {"recommendations":[{"category":"shoes","item_id":999999,"reason":"bad"}],"alternatives":[],"next_question":"q?"}
                """);

        mockMvc.perform(post("/api/v1/recommendations")
                        .header("Authorization", "Bearer token-user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"shoes","budgetMax":200000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].itemId").value(first.getId()))
                .andExpect(jsonPath("$.recommendations[0].reason").value("budget/category/constraints based fallback"));
    }

    @Test
    void normalRecommendationSavesHistory() throws Exception {
        firebaseTokenVerifier.allow("token-user-a", "firebase-user-a");
        CatalogItem first = catalogItemRepository.save(new CatalogItem(
                CatalogItemType.SHOES, "shoe-1", "brand-a", "50000-120000", "[\"minimal\",\"black\"]", "UNISEX", "SS"
        ));
        catalogItemRepository.save(new CatalogItem(
                CatalogItemType.SHOES, "shoe-2", "brand-b", "60000-130000", "[\"street\",\"navy\"]", "UNISEX", "FW"
        ));
        createProfile("token-user-a", "firebase-user-a");
        stubRecommendationLlmClient.setResponse("""
                {"recommendations":[{"category":"shoes","item_id":%d,"reason":"fits your style"}],"alternatives":[],"next_question":"다음은 어떤 색상을 원해요?"}
                """.formatted(first.getId()));

        mockMvc.perform(post("/api/v1/recommendations")
                        .header("Authorization", "Bearer token-user-a")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"shoes","budgetMax":200000}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recommendations[0].itemId").value(first.getId()))
                .andExpect(jsonPath("$.nextQuestion").value("다음은 어떤 색상을 원해요?"));

        Assertions.assertEquals(1, recommendationHistoryRepository.count());
        RecommendationHistory history = recommendationHistoryRepository.findAll().getFirst();
        Assertions.assertTrue(history.getRequestJson().contains("\"category\":\"shoes\""));
        Assertions.assertTrue(history.getResultJson().contains("\"itemId\":" + first.getId()));
    }

    private void createProfile(String token, String firebaseUid) throws Exception {
        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        UserAccount user = userAccountRepository.findByFirebaseUid(firebaseUid).orElseThrow();
        preferenceProfileRepository.save(new PreferenceProfile(
                user,
                1,
                """
                {"style_archetypes":["minimal"],"colors":{"like":["black"],"avoid":[]},"fit":{"top":"regular","pants":"wide"},"brands":{"like":[],"avoid":[]},"budget":{"min":50000,"max":200000},"context":{"ageRange":"20s","occasion":["daily"]},"constraints":["no_leather"],"confidence":0.8}
                """,
                "seed-profile"
        ));
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
        StubRecommendationLlmClient stubRecommendationLlmClient() {
            return new StubRecommendationLlmClient();
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

    static class StubRecommendationLlmClient implements RecommendationLlmClient {
        private String response = "{\"recommendations\":[],\"alternatives\":[],\"next_question\":\"q?\"}";

        void setResponse(String response) {
            this.response = response;
        }

        void clear() {
            response = "{\"recommendations\":[],\"alternatives\":[],\"next_question\":\"q?\"}";
        }

        @Override
        public String pickFromCandidates(String systemPrompt, String userPrompt) {
            return response;
        }
    }
}
