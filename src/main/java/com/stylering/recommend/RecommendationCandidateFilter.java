package com.stylering.recommend;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stylering.api.dto.PostRecommendationsRequest;
import com.stylering.catalog.CatalogItem;
import com.stylering.catalog.CatalogItemType;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class RecommendationCandidateFilter {

    private static final int MAX_CANDIDATES = 30;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public List<CatalogItem> filter(
            List<CatalogItem> allItems,
            Map<String, Object> profileMap,
            PostRecommendationsRequest request
    ) {
        CatalogItemType requestedType = parseType(request.category());
        Integer budgetMax = resolveBudgetMax(profileMap, request.budgetMax());
        Set<String> constraints = lowerSet(readStringList(profileMap.get("constraints")));
        Set<String> preferences = collectPreferences(profileMap);

        List<ScoredCandidate> scored = new ArrayList<>();
        for (CatalogItem item : allItems) {
            if (requestedType != null && item.getType() != requestedType) {
                continue;
            }
            if (budgetMax != null && extractPriceMax(item.getPriceRange()) > budgetMax) {
                continue;
            }

            Set<String> tags = lowerSet(readItemTags(item.getTagsJson()));
            if (!disjoint(tags, constraints)) {
                continue;
            }

            int score = 0;
            for (String pref : preferences) {
                if (tags.contains(pref)) {
                    score++;
                }
            }
            scored.add(new ScoredCandidate(item, score));
        }

        scored.sort(Comparator.comparingInt(ScoredCandidate::score).reversed()
                .thenComparing(c -> c.item().getId()));

        List<CatalogItem> result = new ArrayList<>();
        for (ScoredCandidate candidate : scored) {
            result.add(candidate.item());
            if (result.size() >= MAX_CANDIDATES) {
                break;
            }
        }
        return result;
    }

    private CatalogItemType parseType(String category) {
        if (category == null || category.isBlank()) {
            return null;
        }
        try {
            return CatalogItemType.valueOf(category.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private Integer resolveBudgetMax(Map<String, Object> profileMap, Integer requestBudgetMax) {
        if (requestBudgetMax != null) {
            return requestBudgetMax;
        }
        Object budgetObj = profileMap.get("budget");
        if (!(budgetObj instanceof Map<?, ?> budget)) {
            return null;
        }
        Object maxObj = budget.get("max");
        return maxObj instanceof Number n ? n.intValue() : null;
    }

    @SuppressWarnings("unchecked")
    private Set<String> collectPreferences(Map<String, Object> profileMap) {
        Set<String> prefs = new HashSet<>();
        prefs.addAll(lowerSet(readStringList(profileMap.get("style_archetypes"))));
        Object colorsObj = profileMap.get("colors");
        if (colorsObj instanceof Map<?, ?> colors) {
            prefs.addAll(lowerSet(readStringList(colors.get("like"))));
        }
        return prefs;
    }

    private List<String> readItemTags(String tagsJson) {
        try {
            return objectMapper.readValue(tagsJson, new TypeReference<>() {
            });
        } catch (Exception ex) {
            return List.of();
        }
    }

    private List<String> readStringList(Object value) {
        if (!(value instanceof List<?> list)) {
            return List.of();
        }
        List<String> out = new ArrayList<>();
        for (Object o : list) {
            if (o instanceof String s && !s.isBlank()) {
                out.add(s);
            }
        }
        return out;
    }

    private Set<String> lowerSet(List<String> values) {
        Set<String> out = new HashSet<>();
        for (String value : values) {
            out.add(value.toLowerCase(Locale.ROOT));
        }
        return out;
    }

    private boolean disjoint(Set<String> left, Set<String> right) {
        for (String value : left) {
            if (right.contains(value)) {
                return false;
            }
        }
        return true;
    }

    private int extractPriceMax(String priceRange) {
        if (priceRange == null || priceRange.isBlank()) {
            return Integer.MAX_VALUE;
        }
        String[] parts = priceRange.split("-");
        if (parts.length == 2) {
            try {
                return Integer.parseInt(parts[1].trim());
            } catch (NumberFormatException ignored) {
                return Integer.MAX_VALUE;
            }
        }
        try {
            return Integer.parseInt(priceRange.trim());
        } catch (NumberFormatException ignored) {
            return Integer.MAX_VALUE;
        }
    }

    private record ScoredCandidate(CatalogItem item, int score) {
    }
}
