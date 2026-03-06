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
import java.util.Optional;
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
        Set<String> materialsAvoid = collectMaterialsAvoid(profileMap);
        Set<String> preferences = collectPreferences(profileMap);
        Set<String> occasions = request.occasions() != null ? lowerSet(request.occasions()) : Set.of();
        Set<String> brandsLike = collectBrandsLike(profileMap);
        Set<String> brandsAvoid = collectBrandsAvoid(profileMap);

        List<ScoredCandidate> scored = new ArrayList<>();
        for (CatalogItem item : allItems) {
            if (requestedType != null && item.getType() != requestedType) {
                continue;
            }
            if (budgetMax != null && extractPriceMax(item.getPriceRange()) > budgetMax) {
                continue;
            }

            // skip avoided brands
            if (!brandsAvoid.isEmpty() && brandsAvoid.contains(item.getBrand().toLowerCase(Locale.ROOT))) {
                continue;
            }

            Set<String> tags = lowerSet(readItemTags(item.getTagsJson()));
            if (!disjoint(tags, constraints)) {
                continue;
            }
            if (!materialsAvoid.isEmpty() && !disjoint(tags, materialsAvoid)) {
                continue;
            }

            int score = 0;
            for (String pref : preferences) {
                if (tags.contains(pref)) {
                    score++;
                }
            }
            for (String occasion : occasions) {
                if (tags.contains(occasion)) {
                    score += 2;
                }
            }
            // brand preference bonus
            if (!brandsLike.isEmpty() && brandsLike.contains(item.getBrand().toLowerCase(Locale.ROOT))) {
                score += 3;
            }
            score += fitBonusForItem(profileMap, item.getType(), tags);
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
        Object materialObj = profileMap.get("material_pref");
        if (materialObj instanceof Map<?, ?> material) {
            prefs.addAll(lowerSet(readStringList(material.get("like"))));
        }
        return prefs;
    }

    @SuppressWarnings("unchecked")
    private Set<String> collectMaterialsAvoid(Map<String, Object> profileMap) {
        Object materialObj = profileMap.get("material_pref");
        if (!(materialObj instanceof Map<?, ?> material)) {
            return Set.of();
        }
        return lowerSet(readStringList(material.get("avoid")));
    }

    @SuppressWarnings("unchecked")
    private Set<String> collectBrandsLike(Map<String, Object> profileMap) {
        Object brandsObj = profileMap.get("brands");
        if (!(brandsObj instanceof Map<?, ?> brands)) {
            return Set.of();
        }
        return lowerSet(readStringList(brands.get("like")));
    }

    @SuppressWarnings("unchecked")
    private Set<String> collectBrandsAvoid(Map<String, Object> profileMap) {
        Object brandsObj = profileMap.get("brands");
        if (!(brandsObj instanceof Map<?, ?> brands)) {
            return Set.of();
        }
        return lowerSet(readStringList(brands.get("avoid")));
    }

    @SuppressWarnings("unchecked")
    private Optional<String> fitPreferenceByType(Map<String, Object> profileMap, CatalogItemType type) {
        if (type != CatalogItemType.TOP && type != CatalogItemType.PANTS) {
            return Optional.empty();
        }

        Object fitObj = profileMap.get("fit");
        if (!(fitObj instanceof Map<?, ?> fit)) {
            return Optional.empty();
        }

        Object raw = type == CatalogItemType.TOP ? fit.get("top") : fit.get("pants");
        if (raw instanceof String s && !s.isBlank()) {
            return Optional.of(s.toLowerCase(Locale.ROOT));
        }
        return Optional.empty();
    }

    private int fitBonusForItem(Map<String, Object> profileMap, CatalogItemType type, Set<String> tags) {
        Optional<String> fitPreference = fitPreferenceByType(profileMap, type);
        if (fitPreference.isPresent() && tags.contains(fitPreference.get())) {
            return 1;
        }
        return 0;
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
