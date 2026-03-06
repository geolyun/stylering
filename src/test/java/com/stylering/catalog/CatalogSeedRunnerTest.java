package com.stylering.catalog;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.boot.ApplicationArguments;

class CatalogSeedRunnerTest {

    @Test
    void seedsMissingItemsEvenWhenCatalogAlreadyHasRows() throws Exception {
        CatalogItemRepository repository = Mockito.mock(CatalogItemRepository.class);
        CatalogSeedRunner runner = new CatalogSeedRunner(repository, true);

        CatalogItem existing = new CatalogItem(
                CatalogItemType.TOP,
                "custom-item",
                "custom-brand",
                "10000-20000",
                "[\"casual\"]",
                "UNISEX",
                "SS"
        );

        Mockito.when(repository.findAll()).thenReturn(List.of(existing));
        Mockito.when(repository.saveAll(ArgumentMatchers.<CatalogItem>anyList()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        runner.run(Mockito.mock(ApplicationArguments.class));

        Mockito.verify(repository).saveAll(ArgumentMatchers.argThat(items ->
                items instanceof List<?> list && list.size() == 100
        ));
    }
}
