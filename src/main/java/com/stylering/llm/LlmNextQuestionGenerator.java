package com.stylering.llm;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Component;

@Component
public class LlmNextQuestionGenerator implements NextQuestionGenerator {

    private static final Logger log = LoggerFactory.getLogger(LlmNextQuestionGenerator.class);
    private final PromptTemplateLoader promptTemplateLoader;
    private final QuestionLlmClient questionLlmClient;
    private final String fallbackQuestion;
    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();
    private final Timer durationTimer;
    private final Counter fallbackCounter;

    public LlmNextQuestionGenerator(
            PromptTemplateLoader promptTemplateLoader,
            QuestionLlmClient questionLlmClient,
            @Value("${chat.fallback-question:\uC694\uC998 \uAC00\uC7A5 \uC790\uC8FC \uC785\uB294 \uC2A4\uD0C0\uC77C\uC744 \uD55C \uAC00\uC9C0\uB85C \uB9D0\uD574\uC904\uB798\uC694?}") String fallbackQuestion,
            MeterRegistry meterRegistry
    ) {
        this.promptTemplateLoader = promptTemplateLoader;
        this.questionLlmClient = questionLlmClient;
        this.fallbackQuestion = fallbackQuestion;
        this.durationTimer = Timer.builder("llm.question.duration")
                .description("Duration of LLM question generation")
                .register(meterRegistry);
        this.fallbackCounter = Counter.builder("llm.question.fallback")
                .description("Number of fallbacks in LLM question generation")
                .register(meterRegistry);
    }

    @Override
    public AssistantTurn generate(String userMessage, String conversationHistory) {
        return generate(userMessage, conversationHistory, "");
    }

    @Override
    public AssistantTurn generate(String userMessage, String conversationHistory,
                                   String followupQuestions, String confirmedAxes) {
        long start = System.nanoTime();
        try {
            String raw = questionLlmClient.generateNextQuestion(
                    promptTemplateLoader.systemPrompt(),
                    promptTemplateLoader.buildAskQuestionPrompt(userMessage, conversationHistory, followupQuestions, confirmedAxes)
            );
            durationTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return parseAssistantTurn(raw);
        } catch (RuntimeException ex) {
            durationTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            log.warn("Question generation failed. Fallback question will be returned.", ex);
            fallbackCounter.increment();
            return fallbackTurn();
        }
    }

    @Override
    public AssistantTurn generate(String userMessage, String conversationHistory, String followupQuestions) {
        long start = System.nanoTime();
        try {
            String raw = questionLlmClient.generateNextQuestion(
                    promptTemplateLoader.systemPrompt(),
                    promptTemplateLoader.buildAskQuestionPrompt(userMessage, conversationHistory, followupQuestions)
            );
            durationTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return parseAssistantTurn(raw);
        } catch (RuntimeException ex) {
            durationTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            log.warn("Question generation failed. Fallback question will be returned.", ex);
            fallbackCounter.increment();
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
            log.warn("Failed to parse LLM response as expected JSON. Returning best-effort content.");
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
