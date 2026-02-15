package com.stylering.llm;

public interface QuestionLlmClient {
    String generateNextQuestion(String systemPrompt, String userPrompt);
}
