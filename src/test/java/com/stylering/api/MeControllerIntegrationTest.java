package com.stylering.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;

import com.stylering.auth.FirebaseTokenVerifier;
import com.stylering.auth.TokenVerificationException;
import com.stylering.auth.VerifiedFirebaseToken;
import com.stylering.user.UserAccount;
import com.stylering.user.UserAccountRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

@SpringBootTest
@Import(MeControllerIntegrationTest.TestBeans.class)
class MeControllerIntegrationTest {

    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private UserAccountRepository userAccountRepository;

    @Autowired
    private StubFirebaseTokenVerifier firebaseTokenVerifier;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        this.mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext)
                .apply(springSecurity())
                .build();
        firebaseTokenVerifier.clear();
        userAccountRepository.deleteAll();
    }

    @Test
    void meWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_MISSING_TOKEN"));
    }

    @Test
    void meWithInvalidTokenReturnsUnauthorized() throws Exception {
        firebaseTokenVerifier.setRejectAll(true);

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer invalid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("AUTH_INVALID_TOKEN"));
    }

    @Test
    void meWithValidTokenReturnsUserAndUpsertsUserAccount() throws Exception {
        String firebaseUid = "firebase-user-123";
        firebaseTokenVerifier.allow("valid-token", firebaseUid);

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firebaseUid").value(firebaseUid))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.lastLoginAt").isNotEmpty());

        Optional<UserAccount> created = userAccountRepository.findByFirebaseUid(firebaseUid);
        Instant firstLastLoginAt = created.orElseThrow().getLastLoginAt();

        Thread.sleep(5L);

        mockMvc.perform(get("/api/v1/me")
                        .header("Authorization", "Bearer valid-token"))
                .andExpect(status().isOk());

        UserAccount updated = userAccountRepository.findByFirebaseUid(firebaseUid).orElseThrow();
        Instant secondLastLoginAt = updated.getLastLoginAt();

        org.junit.jupiter.api.Assertions.assertTrue(!secondLastLoginAt.isBefore(firstLastLoginAt));
    }

    @TestConfiguration
    static class TestBeans {
        @Bean
        @Primary
        StubFirebaseTokenVerifier stubFirebaseTokenVerifier() {
            return new StubFirebaseTokenVerifier();
        }
    }

    static class StubFirebaseTokenVerifier implements FirebaseTokenVerifier {

        private final Map<String, String> tokenToUid = new ConcurrentHashMap<>();
        private volatile boolean rejectAll;

        void allow(String token, String firebaseUid) {
            tokenToUid.put(token, firebaseUid);
        }

        void setRejectAll(boolean rejectAll) {
            this.rejectAll = rejectAll;
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
}
