package com.stylering.profile;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylering.api.dto.CreateChatSessionRequest;
import com.stylering.chat.ChatMessage;
import com.stylering.chat.ChatMessageRepository;
import com.stylering.chat.ChatSession;
import com.stylering.common.error.ApiClientException;
import com.stylering.llm.ProfileLlmClient;
import com.stylering.llm.PromptTemplateLoader;
import com.stylering.user.UserAccount;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
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
    private final Timer durationTimer;
    private final Counter fallbackCounter;

    public PreferenceProfileService(
            PreferenceProfileRepository preferenceProfileRepository,
            ChatMessageRepository chatMessageRepository,
            ProfileLlmClient profileLlmClient,
            PromptTemplateLoader promptTemplateLoader,
            @Value("${profile.recent-message-limit:10}") int recentMessageLimit,
            @Value("${profile.update-message-step:5}") int updateMessageStep,
            @Value("${profile.min-update-minutes:5}") long minUpdateMinutes,
            MeterRegistry meterRegistry
    ) {
        this.preferenceProfileRepository = preferenceProfileRepository;
        this.chatMessageRepository = chatMessageRepository;
        this.profileLlmClient = profileLlmClient;
        this.promptTemplateLoader = promptTemplateLoader;
        this.recentMessageLimit = recentMessageLimit;
        this.updateMessageStep = updateMessageStep;
        this.minUpdateInterval = Duration.ofMinutes(minUpdateMinutes);
        this.durationTimer = Timer.builder("llm.profile.duration")
                .description("Duration of LLM profile generation")
                .register(meterRegistry);
        this.fallbackCounter = Counter.builder("llm.profile.fallback")
                .description("Number of fallbacks in LLM profile generation")
                .register(meterRegistry);
    }

    @Transactional
    public boolean tryRefreshProfile(UserAccount userAccount, ChatSession session) {
        long messageCount = chatMessageRepository.countBySession_Id(session.getId());
        if (messageCount < updateMessageStep) {
            return false;
        }

        PreferenceProfile existing = preferenceProfileRepository.findByUser_Id(userAccount.getId()).orElse(null);
        if (!shouldUpdate(messageCount, existing)) {
            return false;
        }

        return refreshProfile(userAccount, session, existing, "INCREMENTAL");
    }

    @Transactional
    public boolean finalizeProfile(UserAccount userAccount, ChatSession session) {
        PreferenceProfile existing = preferenceProfileRepository.findByUser_Id(userAccount.getId()).orElse(null);
        return refreshProfile(userAccount, session, existing, "FINAL");
    }

    private boolean refreshProfile(
            UserAccount userAccount,
            ChatSession session,
            PreferenceProfile existing,
            String mode
    ) {
        List<ChatMessage> recentDesc = chatMessageRepository.findBySession_IdOrderByCreatedAtDesc(
                session.getId(),
                PageRequest.of(0, recentMessageLimit)
        );
        if (recentDesc.isEmpty()) {
            return false;
        }

        List<ChatMessage> ordered = new ArrayList<>(recentDesc);
        ordered.sort((a, b) -> a.getCreatedAt().compareTo(b.getCreatedAt()));

        String conversation = ordered.stream()
                .map(m -> m.getRole().name() + ": " + m.getContent())
                .reduce((a, b) -> a + "\n" + b)
                .orElse("");

        String basePrompt = promptTemplateLoader.buildProfilePrompt(conversation, mode);
        String systemPrompt = promptTemplateLoader.systemPrompt();
        ProfileDraft draft = generateProfileDraft(systemPrompt, basePrompt);
        upsertProfile(userAccount, existing, draft.profileJson(), draft.summary());
        return true;
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

    @Transactional(readOnly = true)
    public String getFollowupQuestionsText(UserAccount userAccount) {
        return preferenceProfileRepository.findByUser_Id(userAccount.getId())
                .map(profile -> {
                    try {
                        Map<String, Object> parsed = jsonParser.parseMap(profile.getProfileJson());
                        List<Object> questions = listOrEmpty(parsed.get("followup_questions"));
                        if (questions.isEmpty()) {
                            return "";
                        }
                        StringBuilder sb = new StringBuilder();
                        for (Object q : questions) {
                            if (q instanceof String s && !s.isBlank()) {
                                sb.append("- ").append(s).append("\n");
                            }
                        }
                        return sb.toString().trim();
                    } catch (RuntimeException ex) {
                        return "";
                    }
                })
                .orElse("");
    }

    @Transactional
    public void seedFromStructured(UserAccount userAccount, CreateChatSessionRequest request) {
        int score = 0;

        String fitTop = request.fitTop() != null ? request.fitTop() : "";
        String fitPants = request.fitPants() != null ? request.fitPants() : "";
        if (!fitTop.isBlank()) score++;
        if (!fitPants.isBlank()) score++;

        String heightVal = request.height() != null ? request.height() : "";
        if (!heightVal.isBlank()) score++;

        List<String> occasions = request.occasions() != null
                ? request.occasions().stream().filter(s -> s != null && !s.isBlank()).toList()
                : List.of();
        if (!occasions.isEmpty()) score++;

        boolean hasBudget = request.budgetMin() != null || request.budgetMax() != null;
        if (hasBudget) score++;

        // seed confidence 상한은 0.50; 기존 프로필 confidence는 Math.max로 유지 (퇴보 방지)
        double seedConfidence = Math.min(score * 0.10, 0.50);

        LinkedHashMap<String, Object> newBodyType = new LinkedHashMap<>();
        newBodyType.put("height", heightVal);

        PreferenceProfile existing = preferenceProfileRepository.findByUser_Id(userAccount.getId()).orElse(null);

        // 기존 프로필 파싱 (실패 시 null → 모두 기본값 사용)
        Map<String, Object> prev = null;
        if (existing != null) {
            try {
                prev = jsonParser.parseMap(existing.getProfileJson());
            } catch (RuntimeException ignored) {
                // prev remains null
            }
        }

        double confidence = prev != null
                ? Math.max(seedConfidence, numberOrDefault(prev.get("confidence"), 0.0))
                : seedConfidence;

        LinkedHashMap<String, Object> normalized = new LinkedHashMap<>();

        // LLM 유래 필드: 기존 프로필에서 보존
        normalized.put("style_archetypes", prev != null ? listOrEmpty(prev.get("style_archetypes")) : List.of());
        normalized.put("colors", prev != null ? mapOrDefault(prev.get("colors"), Map.of("like", List.of(), "avoid", List.of())) : Map.of("like", List.of(), "avoid", List.of()));
        normalized.put("brands", prev != null ? mapOrDefault(prev.get("brands"), Map.of("like", List.of(), "avoid", List.of())) : Map.of("like", List.of(), "avoid", List.of()));
        normalized.put("constraints", prev != null ? listOrEmpty(prev.get("constraints")) : List.of());
        normalized.put("followup_questions", prev != null ? listOrEmpty(prev.get("followup_questions")) : List.of());
        normalized.put("material_pref", prev != null ? mapOrDefault(prev.get("material_pref"), Map.of("prefer", List.of(), "avoid", List.of())) : Map.of("prefer", List.of(), "avoid", List.of()));
        normalized.put("shopping_intent", prev != null ? listOrEmpty(prev.get("shopping_intent")) : List.of());
        normalized.put("style_references", prev != null ? listOrEmpty(prev.get("style_references")) : List.of());

        // 구조화 입력 필드: 제공된 서브 필드만 override, 나머지는 기존 보존 (field-level patch merge)
        Object prevFitObj = prev != null ? prev.get("fit") : null;
        String existingFitTop = prevFitObj instanceof Map<?, ?> m && m.get("top") instanceof String s ? s : "";
        String existingFitPants = prevFitObj instanceof Map<?, ?> m && m.get("pants") instanceof String s ? s : "";
        normalized.put("fit", Map.of(
                "top", !fitTop.isBlank() ? fitTop : existingFitTop,
                "pants", !fitPants.isBlank() ? fitPants : existingFitPants));

        Object prevBudgetObj = prev != null ? prev.get("budget") : null;
        Integer existingBudgetMin = prevBudgetObj instanceof Map<?, ?> m && m.get("min") instanceof Number n ? n.intValue() : null;
        Integer existingBudgetMax = prevBudgetObj instanceof Map<?, ?> m && m.get("max") instanceof Number n ? n.intValue() : null;
        LinkedHashMap<String, Object> budget = new LinkedHashMap<>();
        budget.put("min", request.budgetMin() != null ? request.budgetMin() : existingBudgetMin);
        budget.put("max", request.budgetMax() != null ? request.budgetMax() : existingBudgetMax);
        normalized.put("budget", budget);

        Object prevContextObj = prev != null ? prev.get("context") : null;
        String existingAgeRange = prevContextObj instanceof Map<?, ?> m && m.get("ageRange") instanceof String s ? s : "";
        List<Object> existingOccasion = prevContextObj instanceof Map<?, ?> m ? listOrEmpty(m.get("occasion")) : List.of();
        LinkedHashMap<String, Object> context = new LinkedHashMap<>();
        context.put("ageRange", existingAgeRange);
        context.put("occasion", !occasions.isEmpty() ? occasions : existingOccasion);
        normalized.put("context", context);

        normalized.put("confidence", confidence);
        // body_type: 구조화 입력에 height가 있으면 override, 없으면 기존 보존
        normalized.put("body_type", !heightVal.isBlank() ? newBodyType
                : (prev != null ? mapOrDefault(prev.get("body_type"), Map.of("height", "")) : Map.of("height", "")));

        String summary = "구조화 입력으로 생성된 초기 프로필";
        upsertProfile(userAccount, existing, toJson(normalized), summary);
    }

    public String getConfirmedAxesText(UserAccount userAccount) {
        return preferenceProfileRepository.findByUser_Id(userAccount.getId())
                .map(profile -> {
                    try {
                        Map<String, Object> parsed = jsonParser.parseMap(profile.getProfileJson());
                        StringBuilder sb = new StringBuilder();

                        Object budgetObj = parsed.get("budget");
                        if (budgetObj instanceof Map<?, ?> budgetMap) {
                            Object max = budgetMap.get("max");
                            Object min = budgetMap.get("min");
                            if (max instanceof Number n) {
                                sb.append("- 예산: 최대 ").append(formatWon(n.longValue())).append("\n");
                            } else if (min instanceof Number n) {
                                sb.append("- 예산: 최소 ").append(formatWon(n.longValue())).append("\n");
                            }
                        }

                        Object fitObj = parsed.get("fit");
                        if (fitObj instanceof Map<?, ?> fitMap) {
                            Object top = fitMap.get("top");
                            Object pants = fitMap.get("pants");
                            if (top instanceof String s && !s.isBlank()) {
                                sb.append("- 상의 핏: ").append(fitKorean(s)).append("\n");
                            }
                            if (pants instanceof String s && !s.isBlank()) {
                                sb.append("- 하의 핏: ").append(fitKorean(s)).append("\n");
                            }
                        }

                        Object bodyTypeObj = parsed.get("body_type");
                        if (bodyTypeObj instanceof Map<?, ?> bodyTypeMap) {
                            Object height = bodyTypeMap.get("height");
                            if (height instanceof String s && !s.isBlank()) {
                                sb.append("- 체형: ").append(heightKorean(s)).append("\n");
                            }
                        }

                        Object contextObj = parsed.get("context");
                        if (contextObj instanceof Map<?, ?> contextMap) {
                            Object occasions = contextMap.get("occasion");
                            if (occasions instanceof List<?> list && !list.isEmpty()) {
                                String occs = list.stream()
                                        .filter(o -> o instanceof String s && !s.isBlank())
                                        .map(o -> occasionKorean((String) o))
                                        .collect(Collectors.joining(", "));
                                if (!occs.isBlank()) {
                                    sb.append("- 착용 상황: ").append(occs).append("\n");
                                }
                            }
                        }

                        return sb.toString().trim();
                    } catch (RuntimeException ex) {
                        return "";
                    }
                })
                .orElse("");
    }

    private String formatWon(long amount) {
        return String.format("%,d원", amount);
    }

    private String fitKorean(String fit) {
        return switch (fit.toLowerCase()) {
            case "slim" -> "슬림";
            case "regular" -> "레귤러";
            case "overfit" -> "오버핏";
            case "wide" -> "와이드";
            case "relaxed" -> "루즈핏";
            default -> fit;
        };
    }

    private String heightKorean(String height) {
        return switch (height.toLowerCase()) {
            case "petite" -> "소형키";
            case "regular" -> "보통키";
            case "tall" -> "큰키";
            default -> height;
        };
    }

    private String occasionKorean(String occasion) {
        return switch (occasion.toLowerCase()) {
            case "daily" -> "데일리";
            case "office" -> "오피스";
            case "date" -> "데이트";
            case "campus" -> "캠퍼스";
            case "outdoor" -> "아웃도어";
            case "travel" -> "여행";
            default -> occasion;
        };
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
        long start = System.nanoTime();
        try {
            String first = profileLlmClient.generateProfileJson(systemPrompt, basePrompt);
            Map<String, Object> parsed = jsonParser.parseMap(first);
            durationTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
            return fromParsedMap(parsed);
        } catch (RuntimeException firstError) {
            try {
                String retryPrompt = basePrompt + "\n\nReturn JSON only. No markdown.";
                String second = profileLlmClient.generateProfileJson(systemPrompt, retryPrompt);
                Map<String, Object> parsedRetry = jsonParser.parseMap(second);
                durationTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                return fromParsedMap(parsedRetry);
            } catch (RuntimeException secondError) {
                durationTimer.record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
                fallbackCounter.increment();
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
        normalized.put("followup_questions", listOrEmpty(parsed.get("followup_questions")));
        normalized.put("body_type", mapOrDefault(parsed.get("body_type"), Map.of("height", "")));
        normalized.put("material_pref", mapOrDefault(parsed.get("material_pref"), Map.of("prefer", List.of(), "avoid", List.of())));
        normalized.put("shopping_intent", listOrEmpty(parsed.get("shopping_intent")));
        normalized.put("style_references", listOrEmpty(parsed.get("style_references")));

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
        fallback.put("followup_questions", List.of());
        fallback.put("body_type", Map.of("height", ""));
        fallback.put("material_pref", Map.of("prefer", List.of(), "avoid", List.of()));
        fallback.put("shopping_intent", List.of());
        fallback.put("style_references", List.of());
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
