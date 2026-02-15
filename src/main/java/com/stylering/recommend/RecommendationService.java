package com.stylering.recommend;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylering.api.dto.PostRecommendationsRequest;
import com.stylering.catalog.CatalogItem;
import com.stylering.catalog.CatalogItemRepository;
import com.stylering.chat.ChatSession;
import com.stylering.chat.ChatSessionRepository;
import com.stylering.llm.PromptTemplateLoader;
import com.stylering.llm.RecommendationLlmClient;
import com.stylering.profile.PreferenceProfile;
import com.stylering.profile.PreferenceProfileService;
import com.stylering.user.UserAccount;
import com.stylering.user.UserAccountService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecommendationService {

    private final CatalogItemRepository catalogItemRepository;
    private final RecommendationHistoryRepository recommendationHistoryRepository;
    private final ChatSessionRepository chatSessionRepository;
    private final PreferenceProfileService preferenceProfileService;
    private final UserAccountService userAccountService;
    private final RecommendationLlmClient recommendationLlmClient;
    private final RecommendationCandidateFilter recommendationCandidateFilter;
    private final PromptTemplateLoader promptTemplateLoader;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final JsonParser jsonParser = JsonParserFactory.getJsonParser();
    private final int fallbackCount;

    public RecommendationService(
            CatalogItemRepository catalogItemRepository,
            RecommendationHistoryRepository recommendationHistoryRepository,
            ChatSessionRepository chatSessionRepository,
            PreferenceProfileService preferenceProfileService,
            UserAccountService userAccountService,
            RecommendationLlmClient recommendationLlmClient,
            RecommendationCandidateFilter recommendationCandidateFilter,
            PromptTemplateLoader promptTemplateLoader,
            @Value("${recommendation.fallback-count:3}") int fallbackCount
    ) {
        this.catalogItemRepository = catalogItemRepository;
        this.recommendationHistoryRepository = recommendationHistoryRepository;
        this.chatSessionRepository = chatSessionRepository;
        this.preferenceProfileService = preferenceProfileService;
        this.userAccountService = userAccountService;
        this.recommendationLlmClient = recommendationLlmClient;
        this.recommendationCandidateFilter = recommendationCandidateFilter;
        this.promptTemplateLoader = promptTemplateLoader;
        this.fallbackCount = fallbackCount;
    }

    @Transactional
    public RecommendationResult recommend(String firebaseUid, PostRecommendationsRequest request) {
        UserAccount user = userAccountService.getByFirebaseUid(firebaseUid);
        PreferenceProfile profile = preferenceProfileService.getMyProfile(user);
        Map<String, Object> profileMap = jsonParser.parseMap(profile.getProfileJson());
        List<CatalogItem> candidates = recommendationCandidateFilter.filter(
                catalogItemRepository.findAll(),
                profileMap,
                request
        );

        RecommendationResult result = selectByLlmOrFallback(profile, request, candidates);
        ChatSession session = resolveSession(user, request.sessionId());
        saveHistory(user, session, request, result);
        return result;
    }

    private RecommendationResult selectByLlmOrFallback(
            PreferenceProfile profile,
            PostRecommendationsRequest request,
            List<CatalogItem> candidates
    ) {
        if (candidates.isEmpty()) {
            return new RecommendationResult(
                    List.of(),
                    List.of(),
                    "원하는 스타일 예시를 한 가지 더 알려줄래요?"
            );
        }

        try {
            String requestJson = writeJson(toMap(request));
            String candidatesJson = writeJson(toPromptCandidates(candidates));
            String prompt = promptTemplateLoader.buildRecommendPrompt(profile.getProfileJson(), requestJson, candidatesJson);
            String llmText = recommendationLlmClient.pickFromCandidates(
                    promptTemplateLoader.systemPrompt(),
                    prompt
            );
            return parseAndValidateLlm(llmText, candidates);
        } catch (RuntimeException ex) {
            return fallbackResult(candidates);
        }
    }

    @SuppressWarnings("unchecked")
    private RecommendationResult parseAndValidateLlm(String llmText, List<CatalogItem> candidates) {
        Map<String, CatalogItem> candidateMap = candidates.stream()
                .collect(Collectors.toMap(i -> String.valueOf(i.getId()), i -> i));
        Set<String> candidateIds = candidateMap.keySet();

        Map<String, Object> parsed = jsonParser.parseMap(llmText);
        List<Map<String, Object>> recs = readObjectList(parsed.get("recommendations"));
        List<Map<String, Object>> alts = readObjectList(parsed.get("alternatives"));
        String nextQuestion = parsed.get("next_question") instanceof String s && !s.isBlank()
                ? s
                : "비슷한 다른 스타일도 볼까요?";

        if (!allItemIdsInCandidates(recs, candidateIds) || !allItemIdsInCandidates(alts, candidateIds)) {
            return fallbackResult(candidates);
        }

        List<PickedItem> recommendations = toPickedItems(recs, candidateMap);
        List<PickedItem> alternatives = toPickedItems(alts, candidateMap);
        if (recommendations.isEmpty()) {
            return fallbackResult(candidates);
        }
        return new RecommendationResult(recommendations, alternatives, nextQuestion);
    }

    private List<PickedItem> toPickedItems(List<Map<String, Object>> from, Map<String, CatalogItem> candidateMap) {
        List<PickedItem> out = new ArrayList<>();
        for (Map<String, Object> item : from) {
            Long id = toLong(item.get("item_id"));
            if (id == null) {
                continue;
            }
            CatalogItem catalogItem = candidateMap.get(String.valueOf(id));
            if (catalogItem == null) {
                continue;
            }
            String reason = item.get("reason") instanceof String s && !s.isBlank() ? s : "profile match";
            out.add(new PickedItem(catalogItem, reason));
        }
        return out;
    }

    private boolean allItemIdsInCandidates(List<Map<String, Object>> list, Set<String> candidateIds) {
        for (Map<String, Object> item : list) {
            Long itemId = toLong(item.get("item_id"));
            if (itemId == null || !candidateIds.contains(String.valueOf(itemId))) {
                return false;
            }
        }
        return true;
    }

    private RecommendationResult fallbackResult(List<CatalogItem> candidates) {
        List<PickedItem> picks = candidates.stream()
                .limit(fallbackCount)
                .map(item -> new PickedItem(item, "budget/category/constraints based fallback"))
                .toList();
        List<PickedItem> alternatives = candidates.stream()
                .skip(fallbackCount)
                .limit(fallbackCount)
                .map(item -> new PickedItem(item, "fallback alternative"))
                .toList();
        return new RecommendationResult(
                picks,
                alternatives,
                "취향에 맞게 색상 또는 핏 선호를 더 알려줄래요?"
        );
    }

    private ChatSession resolveSession(UserAccount user, Long sessionId) {
        if (sessionId == null) {
            return null;
        }
        return chatSessionRepository.findByIdAndUser_Id(sessionId, user.getId()).orElse(null);
    }

    private void saveHistory(
            UserAccount user,
            ChatSession session,
            PostRecommendationsRequest request,
            RecommendationResult result
    ) {
        String requestJson = writeJson(toMap(request));
        String resultJson = writeJson(toMap(result));
        recommendationHistoryRepository.save(new RecommendationHistory(user, session, requestJson, resultJson));
    }

    private Map<String, Object> toMap(PostRecommendationsRequest request) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("sessionId", request.sessionId());
        map.put("category", request.category());
        map.put("budgetMax", request.budgetMax());
        return map;
    }

    private Map<String, Object> toMap(RecommendationResult result) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("recommendations", result.recommendations().stream().map(this::toMap).toList());
        map.put("alternatives", result.alternatives().stream().map(this::toMap).toList());
        map.put("nextQuestion", result.nextQuestion());
        return map;
    }

    private Map<String, Object> toMap(PickedItem item) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("itemId", item.item().getId());
        map.put("category", item.item().getType().name().toLowerCase());
        map.put("name", item.item().getName());
        map.put("brand", item.item().getBrand());
        map.put("priceRange", item.item().getPriceRange());
        map.put("reason", item.reason());
        return map;
    }

    private List<Map<String, Object>> toPromptCandidates(List<CatalogItem> candidates) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (CatalogItem item : candidates) {
            Map<String, Object> row = new HashMap<>();
            row.put("item_id", item.getId());
            row.put("category", item.getType().name().toLowerCase());
            row.put("name", item.getName());
            row.put("brand", item.getBrand());
            row.put("price_range", item.getPriceRange());
            row.put("tags_json", item.getTagsJson());
            out.add(row);
        }
        return out;
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to serialize json", ex);
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> readObjectList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<Map<String, Object>> out = new ArrayList<>();
        for (Object obj : list) {
            if (obj instanceof Map<?, ?> m) {
                out.add((Map<String, Object>) m);
            }
        }
        return out;
    }

    private Long toLong(Object value) {
        if (value instanceof Number n) {
            return n.longValue();
        }
        if (value instanceof String s) {
            try {
                return Long.parseLong(s);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public record PickedItem(CatalogItem item, String reason) {
    }

    public record RecommendationResult(
            List<PickedItem> recommendations,
            List<PickedItem> alternatives,
            String nextQuestion
    ) {
    }
}
