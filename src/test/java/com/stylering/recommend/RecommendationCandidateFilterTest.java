package com.stylering.recommend;

import com.stylering.api.dto.PostRecommendationsRequest;
import com.stylering.catalog.CatalogItem;
import com.stylering.catalog.CatalogItemType;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class RecommendationCandidateFilterTest {

    private final RecommendationCandidateFilter filter = new RecommendationCandidateFilter();

    @Test
    void filtersByCategoryBudgetAndConstraints() throws Exception {
        CatalogItem shoesAllowed = withId(new CatalogItem(
                CatalogItemType.SHOES, "shoe-1", "brand-a", "50000-120000", "[\"minimal\",\"black\"]",
                "UNISEX", "SS"
        ), 1L);
        CatalogItem shoesBlocked = withId(new CatalogItem(
                CatalogItemType.SHOES, "shoe-2", "brand-b", "50000-120000", "[\"no_leather\",\"street\"]",
                "UNISEX", "SS"
        ), 2L);
        CatalogItem shoesOverBudget = withId(new CatalogItem(
                CatalogItemType.SHOES, "shoe-3", "brand-c", "50000-250000", "[\"minimal\"]",
                "UNISEX", "FW"
        ), 3L);
        CatalogItem pants = withId(new CatalogItem(
                CatalogItemType.PANTS, "pants-1", "brand-d", "50000-120000", "[\"minimal\"]",
                "UNISEX", "FW"
        ), 4L);

        Map<String, Object> profile = Map.of(
                "style_archetypes", List.of("minimal"),
                "colors", Map.of("like", List.of("black"), "avoid", List.of()),
                "constraints", List.of("no_leather"),
                "budget", Map.of("max", 200000)
        );

        List<CatalogItem> filtered = filter.filter(
                List.of(shoesAllowed, shoesBlocked, shoesOverBudget, pants),
                profile,
                new PostRecommendationsRequest(null, "shoes", 200000, null)
        );

        Assertions.assertEquals(1, filtered.size());
        Assertions.assertEquals(shoesAllowed.getId(), filtered.getFirst().getId());
    }

    @Test
    void fitBonusAppliedOnlyToMatchingApparelCategory() throws Exception {
        CatalogItem shoes = withId(new CatalogItem(
                CatalogItemType.SHOES, "shoe", "brand-a", "50000-120000", "[\"regular\"]",
                "UNISEX", "SS"
        ), 1L);
        CatalogItem top = withId(new CatalogItem(
                CatalogItemType.TOP, "top", "brand-b", "50000-120000", "[\"regular\"]",
                "UNISEX", "SS"
        ), 2L);

        Map<String, Object> profile = Map.of(
                "style_archetypes", List.of(),
                "colors", Map.of("like", List.of(), "avoid", List.of()),
                "constraints", List.of(),
                "fit", Map.of("top", "regular", "pants", "wide"),
                "budget", Map.of("max", 200000)
        );

        List<CatalogItem> filtered = filter.filter(
                List.of(shoes, top),
                profile,
                new PostRecommendationsRequest(null, null, 200000, null)
        );

        Assertions.assertEquals(2, filtered.size());
        Assertions.assertEquals(top.getId(), filtered.getFirst().getId());
        Assertions.assertEquals(shoes.getId(), filtered.get(1).getId());
    }

    private CatalogItem withId(CatalogItem item, Long id) throws Exception {
        Field field = CatalogItem.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(item, id);
        return item;
    }
}
