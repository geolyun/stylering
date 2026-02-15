package com.stylering.llm;

public interface NextQuestionGenerator {
    AssistantTurn generate(String userMessage);

    record AssistantTurn(
            String assistantContent,
            NextAction nextAction,
            String ctaPrimary,
            String ctaSecondary
    ) {
    }

    enum NextAction {
        ASK,
        SUGGEST_STOP,
        RECOMMEND
    }
}
