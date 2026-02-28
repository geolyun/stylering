package com.stylering.api;

import com.stylering.api.dto.PostRecommendationsRequest;
import com.stylering.api.dto.PostRecommendationsResponse;
import com.stylering.api.dto.RecommendationItemResponse;
import com.stylering.catalog.ShoppingLinkResolver;
import com.stylering.recommend.RecommendationService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class RecommendationController {

    private final RecommendationService recommendationService;
    private final ShoppingLinkResolver shoppingLinkResolver;

    public RecommendationController(
            RecommendationService recommendationService,
            ShoppingLinkResolver shoppingLinkResolver
    ) {
        this.recommendationService = recommendationService;
        this.shoppingLinkResolver = shoppingLinkResolver;
    }

    @PostMapping("/recommendations")
    public PostRecommendationsResponse recommend(
            Authentication authentication,
            @Valid @RequestBody PostRecommendationsRequest request
    ) {
        String firebaseUid = String.valueOf(authentication.getPrincipal());
        RecommendationService.RecommendationResult result = recommendationService.recommend(firebaseUid, request);
        return new PostRecommendationsResponse(
                toResponses(result.recommendations()),
                toResponses(result.alternatives()),
                result.nextQuestion()
        );
    }

    private List<RecommendationItemResponse> toResponses(List<RecommendationService.PickedItem> picks) {
        return picks.stream()
                .map(pick -> new RecommendationItemResponse(
                        pick.item().getId(),
                        pick.item().getType().name().toLowerCase(),
                        pick.item().getName(),
                        pick.item().getBrand(),
                        pick.item().getPriceRange(),
                        pick.reason(),
                        shoppingLinkResolver.resolve(pick.item())
                ))
                .toList();
    }
}
