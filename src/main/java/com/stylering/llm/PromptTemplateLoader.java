package com.stylering.llm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

@Component
public class PromptTemplateLoader {

    private static final String USER_MESSAGE_PLACEHOLDER = "{{user_message}}";
    private static final String CONVERSATION_PLACEHOLDER = "{{conversation}}";
    private static final String MODE_PLACEHOLDER = "{{mode}}";
    private static final String PROFILE_JSON_PLACEHOLDER = "{{profile_json}}";
    private static final String REQUEST_JSON_PLACEHOLDER = "{{request_json}}";
    private static final String CANDIDATES_JSON_PLACEHOLDER = "{{candidates_json}}";

    private final String systemPrompt;
    private final String askQuestionsTemplate;
    private final String buildProfileTemplate;
    private final String recommendTemplate;

    public PromptTemplateLoader(
            ResourceLoader resourceLoader,
            @Value("${prompts.path:classpath:prompts}") String promptsPath
    ) {
        this.systemPrompt = readRequired(resourceLoader, promptsPath, "system.md");
        this.askQuestionsTemplate = readRequired(resourceLoader, promptsPath, "ask_questions.md");
        this.buildProfileTemplate = readRequired(resourceLoader, promptsPath, "build_profile.md");
        this.recommendTemplate = readRequired(resourceLoader, promptsPath, "recommend.md");
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String buildAskQuestionPrompt(String userMessage, String conversationHistory) {
        return askQuestionsTemplate
                .replace(CONVERSATION_PLACEHOLDER, conversationHistory)
                .replace(USER_MESSAGE_PLACEHOLDER, userMessage);
    }

    public String buildProfilePrompt(String conversation) {
        return buildProfilePrompt(conversation, "INCREMENTAL");
    }

    public String buildProfilePrompt(String conversation, String mode) {
        return buildProfileTemplate
                .replace(CONVERSATION_PLACEHOLDER, conversation)
                .replace(MODE_PLACEHOLDER, mode);
    }

    public String buildRecommendPrompt(String profileJson, String requestJson, String candidatesJson) {
        return recommendTemplate
                .replace(PROFILE_JSON_PLACEHOLDER, profileJson)
                .replace(REQUEST_JSON_PLACEHOLDER, requestJson)
                .replace(CANDIDATES_JSON_PLACEHOLDER, candidatesJson);
    }

    private String readRequired(ResourceLoader resourceLoader, String basePath, String fileName) {
        String location = resolveLocation(basePath, fileName);
        Resource resource = resourceLoader.getResource(location);
        try {
            if (!resource.exists()) {
                throw new IllegalStateException("Prompt resource not found: " + location);
            }
            try (InputStream in = resource.getInputStream()) {
                return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load prompt resource: " + location, ex);
        }
    }

    private String resolveLocation(String basePath, String fileName) {
        if (basePath.endsWith("/")) {
            return basePath + fileName;
        }
        return basePath + "/" + fileName;
    }
}
