package com.stylering.config;

import com.stylering.llm.LlmProvider;
import com.stylering.llm.OllamaProfileLlmClient;
import com.stylering.llm.OllamaQuestionLlmClient;
import com.stylering.llm.OllamaRecommendationLlmClient;
import com.stylering.llm.OpenAiProfileLlmClient;
import com.stylering.llm.OpenAiQuestionLlmClient;
import com.stylering.llm.OpenAiRecommendationLlmClient;
import com.stylering.llm.ProfileLlmClient;
import com.stylering.llm.QuestionLlmClient;
import com.stylering.llm.RecommendationLlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.context.annotation.Profile;
import org.springframework.util.StringUtils;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class LlmConfig {
    private static final Logger log = LoggerFactory.getLogger(LlmConfig.class);

    @Bean
    @Profile("prod")
    public RestClient openAiRestClient(
            @Value("${llm.provider:OPENAI}") String provider,
            @Value("${llm.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${llm.openai.api-key:}") String apiKey,
            @Value("${llm.timeout-ms:120000}") int timeoutMs
    ) {
        validateProvider(provider, LlmProvider.OPENAI);
        log.info("Initializing OpenAI RestClient: baseUrl={}, timeoutMs={}", baseUrl, timeoutMs);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory);

        if (StringUtils.hasText(apiKey)) {
            builder.defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey.trim());
        }

        return builder.build();
    }

    @Bean
    @Profile("local")
    public RestClient ollamaRestClient(
            @Value("${llm.provider:OLLAMA}") String provider,
            @Value("${llm.ollama.base-url:http://localhost:11434}") String baseUrl,
            @Value("${llm.ollama.timeout-ms:${llm.timeout-ms:120000}}") int timeoutMs
    ) {
        validateProvider(provider, LlmProvider.OLLAMA);
        log.info("Initializing Ollama RestClient: baseUrl={}, timeoutMs={}", baseUrl, timeoutMs);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(timeoutMs);
        requestFactory.setReadTimeout(timeoutMs);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    @Bean
    @Profile("prod")
    public QuestionLlmClient openAiQuestionLlmClient(
            RestClient openAiRestClient,
            @Value("${llm.openai.model:gpt-4o-mini}") String model
    ) {
        return new OpenAiQuestionLlmClient(openAiRestClient, model);
    }

    @Bean
    @Profile("prod")
    public ProfileLlmClient openAiProfileLlmClient(
            RestClient openAiRestClient,
            @Value("${llm.openai.profile-model:${llm.openai.model:gpt-4o-mini}}") String model
    ) {
        return new OpenAiProfileLlmClient(openAiRestClient, model);
    }

    @Bean
    @Profile("prod")
    public RecommendationLlmClient openAiRecommendationLlmClient(
            RestClient openAiRestClient,
            @Value("${llm.openai.recommend-model:${llm.openai.model:gpt-4o-mini}}") String model
    ) {
        return new OpenAiRecommendationLlmClient(openAiRestClient, model);
    }

    @Bean
    @Profile("local")
    public QuestionLlmClient ollamaQuestionLlmClient(
            RestClient ollamaRestClient,
            @Value("${llm.ollama.model:qwen2.5:3b}") String model
    ) {
        return new OllamaQuestionLlmClient(ollamaRestClient, model);
    }

    @Bean
    @Profile("local")
    public ProfileLlmClient ollamaProfileLlmClient(
            RestClient ollamaRestClient,
            @Value("${llm.ollama.model:qwen2.5:3b}") String model
    ) {
        return new OllamaProfileLlmClient(ollamaRestClient, model);
    }

    @Bean
    @Profile("local")
    public RecommendationLlmClient ollamaRecommendationLlmClient(
            RestClient ollamaRestClient,
            @Value("${llm.ollama.model:qwen2.5:3b}") String model
    ) {
        return new OllamaRecommendationLlmClient(ollamaRestClient, model);
    }

    private void validateProvider(String configuredProvider, LlmProvider expected) {
        LlmProvider actual = LlmProvider.valueOf(configuredProvider.trim().toUpperCase());
        if (actual != expected) {
            throw new IllegalStateException(
                    "Invalid llm.provider for active profile. expected=" + expected + ", actual=" + actual
            );
        }
    }
}
