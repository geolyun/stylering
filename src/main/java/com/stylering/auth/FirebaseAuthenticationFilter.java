package com.stylering.auth;

import com.stylering.user.UserAccountService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final FirebaseTokenVerifier tokenVerifier;
    private final UserAccountService userAccountService;
    private final ApiAuthenticationEntryPoint authenticationEntryPoint;

    public FirebaseAuthenticationFilter(
            FirebaseTokenVerifier tokenVerifier,
            UserAccountService userAccountService,
            ApiAuthenticationEntryPoint authenticationEntryPoint
    ) {
        this.tokenVerifier = tokenVerifier;
        this.userAccountService = userAccountService;
        this.authenticationEntryPoint = authenticationEntryPoint;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return !path.startsWith("/api/v1/");
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            String authorization = request.getHeader(HttpHeaders.AUTHORIZATION);
            if (authorization == null || !authorization.startsWith(BEARER_PREFIX)) {
                throw new FirebaseAuthenticationException(
                        "AUTH_MISSING_TOKEN",
                        "Missing or invalid Authorization header"
                );
            }

            String idToken = authorization.substring(BEARER_PREFIX.length()).trim();
            if (idToken.isEmpty()) {
                throw new FirebaseAuthenticationException("AUTH_MISSING_TOKEN", "Missing Firebase ID token");
            }

            VerifiedFirebaseToken verifiedToken;
            try {
                verifiedToken = tokenVerifier.verify(idToken);
            } catch (TokenVerificationException ex) {
                throw new FirebaseAuthenticationException("AUTH_INVALID_TOKEN", "Invalid Firebase ID token");
            }

            UsernamePasswordAuthenticationToken authentication =
                    new UsernamePasswordAuthenticationToken(
                            verifiedToken.firebaseUid(),
                            null,
                            Collections.emptyList()
                    );
            authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authentication);

            userAccountService.upsertOnLogin(verifiedToken.firebaseUid());

            filterChain.doFilter(request, response);
        } catch (FirebaseAuthenticationException ex) {
            SecurityContextHolder.clearContext();
            authenticationEntryPoint.commence(request, response, ex);
        }
    }
}
