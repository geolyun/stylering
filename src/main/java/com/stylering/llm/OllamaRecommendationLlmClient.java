package com.stylering.llm;

import java.util.Map;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class OllamaRecommendationLlmClient implements RecommendationLlmClient {

    private final RestClient ollamaRestClient;
    private final String model;
    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();

    public OllamaRecommendationLlmClient(RestClient ollamaRestClient, String model) {
        this.ollamaRestClient = ollamaRestClient;
        this.model = model;
    }

    @Override
    public String pickFromCandidates(String systemPrompt, String userPrompt) {
        String prompt = systemPrompt + "\n\n" + userPrompt;
        Map<String, Object> request = Map.of(
                "model", model,
                "prompt", prompt,
                "stream", false,
                "format", "json",
                "keep_alive", "10m",
                "options", Map.of("num_predict", 256)
        );

        try {
            String response = ollamaRestClient.post()
                    .uri("/api/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(String.class);
            return extractResponseText(response);
        } catch (Exception ex) {
            throw new LlmClientException("Ollama recommendation request failed: " + summarize(ex), ex);
        }
    }

    private String summarize(Exception ex) {
        String message = ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex.getClass().getSimpleName();
        }
        return ex.getClass().getSimpleName() + " - " + message;
    }

    private String extractResponseText(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            throw new LlmClientException("Ollama response body is empty");
        }
        Map<String, Object> root = jsonParser.parseMap(responseBody);
        Object text = root.get("response");
        if (!(text instanceof String s) || s.isBlank()) {
            throw new LlmClientException("Ollama response field is empty");
        }
        return s;
    }
}
