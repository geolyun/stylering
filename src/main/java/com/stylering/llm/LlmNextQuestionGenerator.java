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
            @Value("${chat.fallback-question:\uC694\uC998 \uAC00\uC7A5 \uC790\uC8FC \uC785\uB294 \uC2A4\uD0C0\uC77C\uC744 \uD55C \uAC00\uC9C0\uB85C \uB9D0\uD574\uC904\uB798\uC694?}") String fallbackQuestion
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
