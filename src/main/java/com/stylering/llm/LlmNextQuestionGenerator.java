package com.stylering.llm;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class LlmNextQuestionGenerator implements NextQuestionGenerator {

    private final PromptTemplateLoader promptTemplateLoader;
    private final QuestionLlmClient questionLlmClient;
    private final String fallbackQuestion;

    public LlmNextQuestionGenerator(
            PromptTemplateLoader promptTemplateLoader,
            QuestionLlmClient questionLlmClient,
            @Value("${chat.fallback-question:요즘 가장 자주 입는 스타일을 한 가지로 말해줄래요?}") String fallbackQuestion
    ) {
        this.promptTemplateLoader = promptTemplateLoader;
        this.questionLlmClient = questionLlmClient;
        this.fallbackQuestion = fallbackQuestion;
    }

    @Override
    public String generate(String userMessage) {
        try {
            String raw = questionLlmClient.generateNextQuestion(
                    promptTemplateLoader.systemPrompt(),
                    promptTemplateLoader.buildAskQuestionPrompt(userMessage)
            );
            return normalize(raw);
        } catch (RuntimeException ex) {
            return fallbackQuestion;
        }
    }

    private String normalize(String raw) {
        if (raw == null) {
            return fallbackQuestion;
        }

        String firstLine = raw.replace("\r", "").lines()
                .map(String::trim)
                .filter(line -> !line.isEmpty())
                .findFirst()
                .orElse("");

        if (firstLine.isBlank()) {
            return fallbackQuestion;
        }
        return firstLine;
    }
}
