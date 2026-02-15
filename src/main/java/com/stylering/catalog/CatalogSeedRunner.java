package com.stylering.catalog;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class CatalogSeedRunner implements ApplicationRunner {

    private final CatalogItemRepository catalogItemRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final boolean seedEnabled;

    public CatalogSeedRunner(
            CatalogItemRepository catalogItemRepository,
            @Value("${catalog.seed.enabled:true}") boolean seedEnabled
    ) {
        this.catalogItemRepository = catalogItemRepository;
        this.seedEnabled = seedEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled || catalogItemRepository.count() > 0) {
            return;
        }

        List<CatalogItem> items = new ArrayList<>();
        for (int i = 1; i <= 60; i++) {
            CatalogItemType type = switch (i % 5) {
                case 0 -> CatalogItemType.TOP;
                case 1 -> CatalogItemType.PANTS;
                case 2 -> CatalogItemType.SHOES;
                case 3 -> CatalogItemType.OUTER;
                default -> CatalogItemType.ACCESSORY;
            };
            String season = i % 2 == 0 ? "FW" : "SS";
            String gender = i % 3 == 0 ? "UNISEX" : "MIXED";
            String tagsJson = toJson(List.of(
                    i % 2 == 0 ? "minimal" : "street",
                    i % 3 == 0 ? "black" : "navy",
                    i % 4 == 0 ? "no_leather" : "daily"
            ));
            int min = 30000 + (i * 2000);
            int max = min + 40000;

            items.add(new CatalogItem(
                    type,
                    type.name().toLowerCase() + "-item-" + i,
                    "brand-" + ((i % 10) + 1),
                    min + "-" + max,
                    tagsJson,
                    gender,
                    season
            ));
        }
        catalogItemRepository.saveAll(items);
    }

    private String toJson(List<String> tags) {
        try {
            return objectMapper.writeValueAsString(tags);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to write tags json", ex);
        }
    }
}
