package com.stylering.llm;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class PromptTemplateLoaderTest {

    @Test
    void loadsSystemAskAndProfilePromptsFromClasspath() {
        PromptTemplateLoader loader = new PromptTemplateLoader(
                new DefaultResourceLoader(),
                "classpath:prompts"
        );

        Assertions.assertFalse(loader.systemPrompt().isBlank());
        Assertions.assertTrue(loader.buildAskQuestionPrompt("hello", "USER: prev\nASSISTANT: reply").contains("hello"));
        Assertions.assertTrue(loader.buildProfilePrompt("USER: hi").contains("USER: hi"));
    }

    @Test
    void throwsClearErrorWhenPromptResourceIsMissing() {
        IllegalStateException ex = Assertions.assertThrows(
                IllegalStateException.class,
                () -> new PromptTemplateLoader(new DefaultResourceLoader(), "classpath:prompts-missing")
        );

        Assertions.assertTrue(ex.getMessage().contains("Prompt resource not found"));
        Assertions.assertTrue(ex.getMessage().contains("classpath:prompts-missing/system.md"));
    }
}
