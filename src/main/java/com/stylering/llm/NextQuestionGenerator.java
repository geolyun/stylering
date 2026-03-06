package com.stylering.llm;

public interface NextQuestionGenerator {
    AssistantTurn generate(String userMessage, String conversationHistory);

    default AssistantTurn generate(String userMessage, String conversationHistory, String followupQuestions) {
        return generate(userMessage, conversationHistory);
    }

    default AssistantTurn generate(String userMessage, String conversationHistory,
                                    String followupQuestions, String confirmedAxes) {
        return generate(userMessage, conversationHistory, followupQuestions);
    }

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
