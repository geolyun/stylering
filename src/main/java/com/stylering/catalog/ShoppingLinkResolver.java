package com.stylering.catalog;

import java.net.URLEncoder;
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
        if (productUrl != null && !productUrl.isBlank() && isSafeUrl(productUrl)) {
            return productUrl;
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
        String lower = url.toLowerCase();
        return lower.startsWith("https://") || lower.startsWith("http://");
    }
}
