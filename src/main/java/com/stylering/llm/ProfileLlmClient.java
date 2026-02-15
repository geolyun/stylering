package com.stylering.llm;

public interface ProfileLlmClient {
    String generateProfileJson(String systemPrompt, String userPrompt);
}
