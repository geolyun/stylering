package com.stylering.api.dto;

import java.util.List;

public record PostRecommendationsResponse(
        List<RecommendationItemResponse> recommendations,
        List<RecommendationItemResponse> alternatives,
        String nextQuestion
) {
}
