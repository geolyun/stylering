package com.stylering.llm;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class PromptTemplateLoader {

    private static final String USER_MESSAGE_PLACEHOLDER = "{{user_message}}";

    private final String systemPrompt;
    private final String askQuestionsTemplate;

    public PromptTemplateLoader(@Value("${prompts.path:docs/prompts}") String promptsPath) {
        Path basePath = Path.of(promptsPath);
        this.systemPrompt = readRequired(basePath.resolve("system.md"));
        this.askQuestionsTemplate = readRequired(basePath.resolve("ask_questions.md"));
    }

    public String systemPrompt() {
        return systemPrompt;
    }

    public String buildAskQuestionPrompt(String userMessage) {
        return askQuestionsTemplate.replace(USER_MESSAGE_PLACEHOLDER, userMessage);
    }

    private String readRequired(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8).trim();
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to load prompt file: " + path, ex);
        }
    }
}
