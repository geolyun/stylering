package com.stylering.auth;

import com.stylering.common.error.ApiErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

@Component
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(
            HttpServletRequest request,
            HttpServletResponse response,
            AuthenticationException authException
    ) throws IOException {
        String code = "AUTH_UNAUTHORIZED";
        String message = "Authentication failed";
        if (authException instanceof FirebaseAuthenticationException firebaseException) {
            code = firebaseException.getCode();
            message = firebaseException.getMessage();
        }

        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.UNAUTHORIZED.value(),
                code,
                message,
                request.getRequestURI()
        );

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(toJson(body));
    }

    private String toJson(ApiErrorResponse body) {
        return "{"
                + "\"timestamp\":\"" + body.timestamp() + "\","
                + "\"status\":" + body.status() + ","
                + "\"code\":\"" + escapeJson(body.code()) + "\","
                + "\"message\":\"" + escapeJson(body.message()) + "\","
                + "\"path\":\"" + escapeJson(body.path()) + "\""
                + "}";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
