package com.stylering.auth;

import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Service;

@Service
public class JwtFirebaseTokenVerifier implements FirebaseTokenVerifier {

    private static final String FIREBASE_JWK_SET_URI =
            "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";

    private final String projectId;
    private final JwtDecoder jwtDecoder;

    public JwtFirebaseTokenVerifier(@Value("${firebase.project-id:}") String projectId) {
        this.projectId = projectId;
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(FIREBASE_JWK_SET_URI).build();
        decoder.setJwtValidator(buildValidator(projectId));
        this.jwtDecoder = decoder;
    }

    @Override
    public VerifiedFirebaseToken verify(String idToken) {
        if (projectId == null || projectId.isBlank()) {
            throw new TokenVerificationException("Firebase project id is not configured");
        }

        Jwt jwt;
        try {
            jwt = jwtDecoder.decode(idToken);
        } catch (Exception ex) {
            throw new TokenVerificationException("Invalid Firebase ID token", ex);
        }

        String firebaseUid = jwt.getSubject();
        if (firebaseUid == null || firebaseUid.isBlank()) {
            throw new TokenVerificationException("Firebase UID is missing in token");
        }

        return new VerifiedFirebaseToken(firebaseUid);
    }

    private OAuth2TokenValidator<Jwt> buildValidator(String projectId) {
        if (projectId == null || projectId.isBlank()) {
            return token -> OAuth2TokenValidatorResult.success();
        }

        String issuer = "https://securetoken.google.com/" + projectId;
        JwtIssuerValidator issuerValidator = new JwtIssuerValidator(issuer);
        JwtTimestampValidator timestampValidator = new JwtTimestampValidator();
        OAuth2TokenValidator<Jwt> audienceValidator = token -> {
            List<String> aud = token.getAudience();
            if (aud != null && aud.contains(projectId)) {
                return OAuth2TokenValidatorResult.success();
            }
            return OAuth2TokenValidatorResult.failure(
                    new OAuth2Error("invalid_token", "Invalid Firebase audience", null)
            );
        };

        return token -> {
            OAuth2TokenValidatorResult issuerResult = issuerValidator.validate(token);
            if (issuerResult.hasErrors()) {
                return issuerResult;
            }

            OAuth2TokenValidatorResult timestampResult = timestampValidator.validate(token);
            if (timestampResult.hasErrors()) {
                return timestampResult;
            }

            return audienceValidator.validate(token);
        };
    }
}
