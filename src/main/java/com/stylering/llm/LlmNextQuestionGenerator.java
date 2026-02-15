package com.stylering.llm;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Component;

@Component
public class LlmNextQuestionGenerator implements NextQuestionGenerator {

    private final PromptTemplateLoader promptTemplateLoader;
    private final QuestionLlmClient questionLlmClient;
    private final String fallbackQuestion;
    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();

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
    public AssistantTurn generate(String userMessage) {
        try {
            String raw = questionLlmClient.generateNextQuestion(
                    promptTemplateLoader.systemPrompt(),
                    promptTemplateLoader.buildAskQuestionPrompt(userMessage)
            );
            return parseAssistantTurn(raw);
        } catch (RuntimeException ex) {
            return fallbackTurn();
        }
    }

    @SuppressWarnings("unchecked")
    private AssistantTurn parseAssistantTurn(String raw) {
        if (raw == null || raw.isBlank()) {
            return fallbackTurn();
        }

        try {
            Map<String, Object> parsed = jsonParser.parseMap(raw);
            String assistantContent = parsed.get("assistantContent") instanceof String s && !s.isBlank()
                    ? s
                    : fallbackQuestion;

            NextAction nextAction = NextAction.ASK;
            if (parsed.get("nextAction") instanceof String actionRaw) {
                try {
                    nextAction = NextAction.valueOf(actionRaw.trim().toUpperCase());
                } catch (IllegalArgumentException ignored) {
                    nextAction = NextAction.ASK;
                }
            }

            String ctaPrimary = null;
            String ctaSecondary = null;
            if (parsed.get("cta") instanceof Map<?, ?> ctaMap) {
                Object primary = ((Map<String, Object>) ctaMap).get("primary");
                Object secondary = ((Map<String, Object>) ctaMap).get("secondary");
                ctaPrimary = primary instanceof String s ? s : null;
                ctaSecondary = secondary instanceof String s ? s : null;
            }

            return new AssistantTurn(assistantContent, nextAction, ctaPrimary, ctaSecondary);
        } catch (RuntimeException ex) {
            String firstLine = raw.replace("\r", "").lines()
                    .map(String::trim)
                    .filter(line -> !line.isEmpty())
                    .findFirst()
                    .orElse("");
            if (firstLine.isBlank()) {
                return fallbackTurn();
            }
            return new AssistantTurn(firstLine, NextAction.ASK, null, null);
        }
    }

    private AssistantTurn fallbackTurn() {
        return new AssistantTurn(fallbackQuestion, NextAction.ASK, null, null);
    }
}
