package com.stylering.catalog;

import java.lang.reflect.Field;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class ShoppingLinkResolverTest {

    @Test
    void validHttpsProductUrlIsReturnedDirectly() {
        ShoppingLinkResolver resolver = new ShoppingLinkResolver("naver");
        CatalogItem item = itemWithProductUrl("brand-a", "shoe-1", "https://shop.example.com/products/123");

        String result = resolver.resolve(item);

        Assertions.assertEquals("https://shop.example.com/products/123", result);
    }

    @Test
    void validHttpProductUrlIsReturnedDirectly() {
        ShoppingLinkResolver resolver = new ShoppingLinkResolver("naver");
        CatalogItem item = itemWithProductUrl("brand-a", "shoe-1", "http://shop.example.com/products/123");

        String result = resolver.resolve(item);

        Assertions.assertEquals("http://shop.example.com/products/123", result);
    }

    @Test
    void unsafeSchemeProductUrlFallsBackToSearchUrl() {
        ShoppingLinkResolver resolver = new ShoppingLinkResolver("naver");
        CatalogItem item = itemWithProductUrl("brand-a", "shoe-1", "javascript:alert(1)");

        String result = resolver.resolve(item);

        Assertions.assertTrue(result.startsWith("https://search.shopping.naver.com/"));
    }

    @Test
    void dataSchemeProductUrlFallsBackToSearchUrl() {
        ShoppingLinkResolver resolver = new ShoppingLinkResolver("naver");
        CatalogItem item = itemWithProductUrl("brand-a", "shoe-1", "data:text/html,<script>alert(1)</script>");

        String result = resolver.resolve(item);

        Assertions.assertTrue(result.startsWith("https://search.shopping.naver.com/"));
    }

    @Test
    void nullProductUrlGeneratesNaverSearchUrl() {
        ShoppingLinkResolver resolver = new ShoppingLinkResolver("naver");
        CatalogItem item = new CatalogItem(CatalogItemType.SHOES, "클래식 로퍼", "ZARA",
                "80000-120000", "[\"minimal\"]", "UNISEX", "SS");

        String result = resolver.resolve(item);

        Assertions.assertTrue(result.startsWith("https://search.shopping.naver.com/search/all?query="));
        Assertions.assertTrue(result.contains("ZARA"));
    }

    @Test
    void musinsaPlatformGeneratesMusinsuSearchUrl() {
        ShoppingLinkResolver resolver = new ShoppingLinkResolver("musinsa");
        CatalogItem item = new CatalogItem(CatalogItemType.TOP, "오버핏 티셔츠", "MUJI",
                "30000-50000", "[\"minimal\"]", "UNISEX", "SS");

        String result = resolver.resolve(item);

        Assertions.assertTrue(result.startsWith("https://www.musinsa.com/search/musinsa/goods?q="));
    }

    @Test
    void unknownPlatformFallsBackToNaver() {
        ShoppingLinkResolver resolver = new ShoppingLinkResolver("coupang");
        CatalogItem item = new CatalogItem(CatalogItemType.TOP, "item", "brand",
                "10000-20000", "[]", "UNISEX", "SS");

        String result = resolver.resolve(item);

        Assertions.assertTrue(result.startsWith("https://search.shopping.naver.com/"));
    }

    @Test
    void koreanBrandAndNameAreUrlEncoded() {
        ShoppingLinkResolver resolver = new ShoppingLinkResolver("naver");
        CatalogItem item = new CatalogItem(CatalogItemType.TOP, "오버핏 셔츠", "무신사 스탠다드",
                "30000-50000", "[]", "UNISEX", "SS");

        String result = resolver.resolve(item);

        Assertions.assertFalse(result.contains(" "), "URL에 공백이 포함되면 안 됩니다");
        Assertions.assertFalse(result.contains("오"), "URL에 인코딩되지 않은 한글이 포함되면 안 됩니다");
    }

    @Test
    void blankProductUrlGeneratesSearchUrl() {
        ShoppingLinkResolver resolver = new ShoppingLinkResolver("naver");
        CatalogItem item = itemWithProductUrl("brand-a", "shoe-1", "   ");

        String result = resolver.resolve(item);

        Assertions.assertTrue(result.startsWith("https://search.shopping.naver.com/"));
    }

    private CatalogItem itemWithProductUrl(String brand, String name, String productUrl) {
        CatalogItem item = new CatalogItem(CatalogItemType.SHOES, name, brand,
                "50000-100000", "[\"minimal\"]", "UNISEX", "SS");
        try {
            Field field = CatalogItem.class.getDeclaredField("productUrl");
            field.setAccessible(true);
            field.set(item, productUrl);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return item;
    }
}
