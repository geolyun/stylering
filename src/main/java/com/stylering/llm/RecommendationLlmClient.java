package com.stylering.llm;

public interface RecommendationLlmClient {
    String pickFromCandidates(String systemPrompt, String userPrompt);
}
