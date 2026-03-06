package com.stylering.api.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;

public record PostRecommendationsRequest(
        Long sessionId,
        String category,
        @Min(value = 1, message = "budgetMax must be positive")
        @Max(value = 100000000, message = "budgetMax is too large")
        Integer budgetMax,
        List<String> occasions
) {
}
