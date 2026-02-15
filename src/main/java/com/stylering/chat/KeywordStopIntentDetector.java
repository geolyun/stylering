package com.stylering.chat;

import java.util.List;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class KeywordStopIntentDetector implements StopIntentDetector {

    private static final List<String> KEYWORDS = List.of(
            "그만", "멈춰", "추천해줘", "끝", "충분해", "stop", "finish"
    );

    @Override
    public boolean isStopIntent(String content) {
        if (content == null || content.isBlank()) {
            return false;
        }
        String lower = content.toLowerCase(Locale.ROOT);
        return KEYWORDS.stream().anyMatch(lower::contains);
    }
}
