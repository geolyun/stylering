package com.stylering.llm;

import java.util.List;
import java.util.Map;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class OpenAiRecommendationLlmClient implements RecommendationLlmClient {

    private final RestClient llmRestClient;
    private final String model;
    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();

    public OpenAiRecommendationLlmClient(RestClient llmRestClient, String model) {
        this.llmRestClient = llmRestClient;
        this.model = model;
    }

    @Override
    public String pickFromCandidates(String systemPrompt, String userPrompt) {
        Map<String, Object> request = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)
                )
        );

        try {
            String response = llmRestClient.post()
                    .uri("/chat/completions")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            return extractContent(response);
        } catch (Exception ex) {
            throw new LlmClientException("LLM recommendation request failed", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new LlmClientException("LLM response body is empty");
        }
        Map<String, Object> root = jsonParser.parseMap(responseBody);
        Object choicesObj = root.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            throw new LlmClientException("LLM response missing choices");
        }
        Object firstChoiceObj = choices.getFirst();
        if (!(firstChoiceObj instanceof Map<?, ?> firstChoice)) {
            throw new LlmClientException("LLM response choice format is invalid");
        }
        Object messageObj = firstChoice.get("message");
        if (!(messageObj instanceof Map<?, ?> message)) {
            throw new LlmClientException("LLM response missing message");
        }
        Object contentObj = message.get("content");
        if (!(contentObj instanceof String content) || content.isBlank()) {
            throw new LlmClientException("LLM response content is empty");
        }
        return content;
    }
}
