package com.stylering.api.dto;

public record RecommendationItemResponse(
        Long itemId,
        String category,
        String name,
        String brand,
        String priceRange,
        String reason,
        String shopUrl
) {
}
