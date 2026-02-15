package com.stylering.profile;

import com.stylering.chat.ChatMessage;
import com.stylering.chat.ChatMessageRepository;
import com.stylering.chat.ChatSession;
import com.stylering.common.error.ApiClientException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylering.llm.ProfileLlmClient;
import com.stylering.llm.PromptTemplateLoader;
import com.stylering.user.UserAccount;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PreferenceProfileService {

    private final PreferenceProfileRepository preferenceProfileRepository;
    private final ChatMessageRepository chatMessageRepository;
    private final ProfileLlmClient profileLlmClient;
    private final PromptTemplateLoader promptTemplateLoader;
    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final int recentMessageLimit;
    private final int updateMessageStep;
    private final Duration minUpdateInterval;

    public PreferenceProfileService(
            PreferenceProfileRepository preferenceProfileRepository,
            ChatMessageRepository chatMessageRepository,
            ProfileLlmClient profileLlmClient,
            PromptTemplateLoader promptTemplateLoader,
            @Value("${profile.recent-message-limit:10}") int recentMessageLimit,
            @Value("${profile.update-message-step:5}") int updateMessageStep,
            @Value("${profile.min-update-minutes:5}") long minUpdateMinutes
    ) {
        this.preferenceProfileRepository = preferenceProfileRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.profileLlmClient = profileLlmClient;
        this.promptTemplateLoader = promptTemplateLoader;
        this.recentMessageLimit = recentMessageLimit;
        this.updateMessageStep = updateMessageStep;
        this.minUpdateInterval = Duration.ofMinutes(minUpdateMinutes);
    }

    @Transactional
    public void tryRefreshProfile(UserAccount userAccount, ChatSession session) {
        long messageCount = chatMessageRepository.countBySession_Id(session.getId());
        if (messageCount < updateMessageStep) {
            return;
        }

        PreferenceProfile existing = preferenceProfileRepository.findByUser_Id(userAccount.getId()).orElse(null);
        if (!shouldUpdate(messageCount, existing)) {
            return;
        }

        List<ChatMessage> recentDesc = chatMessageRepository.findBySession_IdOrderByCreatedAtDesc(
                session.getId(),
                PageRequest.of(0, recentMessageLimit)
        );
        if (recentDesc.isEmpty()) {
            return;
        }

        List<ChatMessage> ordered = new ArrayList<>(recentDesc);
        ordered.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));

        String conversation = ordered.stream()
                .map(m -> m.getRole().name() + ": " + m.getContent())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        String basePrompt = promptTemplateLoader.buildProfilePrompt(conversation);
        String systemPrompt = promptTemplateLoader.systemPrompt();
        ProfileDraft draft = generateProfileDraft(systemPrompt, basePrompt);
        upsertProfile(userAccount, existing, draft.profileJson(), draft.summary());
    }

    @Transactional(readOnly = true)
    public PreferenceProfile getMyProfile(UserAccount userAccount) {
        return preferenceProfileRepository.findByUser_Id(userAccount.getId())
                .orElseThrow(() -> new ApiClientException(
                        HttpStatus.NOT_FOUND,
                        "PROFILE_NOT_FOUND",
                        "Preference profile not found"
                ));
    }

    private boolean shouldUpdate(long messageCount, PreferenceProfile existing) {
        if (existing == null) {
            return true;
        }
        if (messageCount % updateMessageStep == 0) {
            return true;
        }
        Instant cutoff = Instant.now().minus(minUpdateInterval);
        return existing.getUpdatedAt().isBefore(cutoff);
    }

    @SuppressWarnings("unchecked")
    private ProfileDraft generateProfileDraft(String systemPrompt, String basePrompt) {
        try {
            String first = profileLlmClient.generateProfileJson(systemPrompt, basePrompt);
            Map<String, Object> parsed = jsonParser.parseMap(first);
            return fromParsedMap(parsed);
        } catch (RuntimeException firstError) {
            try {
                String retryPrompt = basePrompt + "\n\nReturn JSON only. No markdown.";
                String second = profileLlmClient.generateProfileJson(systemPrompt, retryPrompt);
                Map<String, Object> parsedRetry = jsonParser.parseMap(second);
                return fromParsedMap(parsedRetry);
            } catch (RuntimeException secondError) {
                return fallbackDraft();
            }
        }
    }

    private ProfileDraft fromParsedMap(Map<String, Object> parsed) {
        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();
        normalized.put("style_archetypes", listOrEmpty(parsed.get("style_archetypes")));
        normalized.put("colors", mapOrDefault(parsed.get("colors"), Map.of("like", List.of(), "avoid", List.of())));
        normalized.put("fit", mapOrDefault(parsed.get("fit"), Map.of("top", "", "pants", "")));
        normalized.put("brands", mapOrDefault(parsed.get("brands"), Map.of("like", List.of(), "avoid", List.of())));
        normalized.put("budget", mapOrDefault(parsed.get("budget"), budgetDefaults()));
        normalized.put("context", mapOrDefault(parsed.get("context"), Map.of("ageRange", "", "occasion", List.of())));
        normalized.put("constraints", listOrEmpty(parsed.get("constraints")));
        normalized.put("confidence", numberOrDefault(parsed.get("confidence"), 0.2d));

        String profileJson = toJson(normalized);
        String summary = parsed.get("summary") instanceof String s && !s.isBlank()
                ? s
                : "Profile updated from recent conversation.";

        return new ProfileDraft(profileJson, summary);
    }

    private ProfileDraft fallbackDraft() {
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("style_archetypes", List.of());
        fallback.put("colors", Map.of("like", List.of(), "avoid", List.of()));
        fallback.put("fit", Map.of("top", "", "pants", ""));
        fallback.put("brands", Map.of("like", List.of(), "avoid", List.of()));
        fallback.put("budget", budgetDefaults());
        fallback.put("context", Map.of("ageRange", "", "occasion", List.of()));
        fallback.put("constraints", List.of());
        fallback.put("confidence", 0.1d);
        return new ProfileDraft(
                toJson(fallback),
                "Fallback profile generated due to profile parsing failure."
        );
    }

    private void upsertProfile(UserAccount user, PreferenceProfile existing, String profileJson, String summary) {
        if (existing == null) {
            preferenceProfileRepository.save(new PreferenceProfile(user, 1, profileJson, summary));
            return;
        }

        existing.update(existing.getVersion() + 1, profileJson, summary);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapOrDefault(Object value, Map<String, Object> fallback) {
        return value instanceof Map<?, ?> m ? (Map<String, Object>) m : fallback;
    }

    @SuppressWarnings("unchecked")
    private List<Object> listOrEmpty(Object value) {
        return value instanceof List<?> l ? (List<Object>) l : List.of();
    }

    private double numberOrDefault(Object value, double fallback) {
        return value instanceof Number n ? n.doubleValue() : fallback;
    }

    private Map<String, Object> budgetDefaults() {
        LinkedHashMap<String, Object> budget = new LinkedHashMap<>();
        budget.put("min", null);
        budget.put("max", null);
        return budget;
    }

    private String toJson(Map<String, Object> value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize profile json", ex);
        }
    }

    private record ProfileDraft(String profileJson, String summary) {
    }
}
