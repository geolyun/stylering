package com.stylering.catalog;

import java.net.URLEncoder;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class ShoppingLinkResolver {

    private final String platform;

    public ShoppingLinkResolver(
            @Value("${shopping.default-platform:naver}") String platform
    ) {
        this.platform = platform;
    }

    public String resolve(CatalogItem item) {
        String productUrl = item.getProductUrl();
        if (productUrl != null) {
            String normalized = productUrl.trim();
            if (!normalized.isBlank() && isSafeUrl(normalized)) {
                return normalized;
            }
        }
        String query = URLEncoder.encode(
                item.getBrand() + " " + item.getName(),
                StandardCharsets.UTF_8
        );
        return switch (platform.toLowerCase()) {
            case "musinsa" -> "https://www.musinsa.com/search/musinsa/goods?q=" + query;
            default -> "https://search.shopping.naver.com/search/all?query=" + query;
        };
    }

    private boolean isSafeUrl(String url) {
        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null) {
                return false;
            }
            String lowerScheme = scheme.toLowerCase();
            if (!"http".equals(lowerScheme) && !"https".equals(lowerScheme)) {
                return false;
            }
            return uri.getHost() != null && !uri.getHost().isBlank();
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }
}
