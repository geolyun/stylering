package com.stylering.ratelimit;

import com.stylering.common.error.ApiClientException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Profile("local | test")
public class InMemoryUserRateLimiter implements UserRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;

    private final int maxRequestsPerMinute;
    private final ConcurrentHashMap<Long, Deque<Long>> userWindows = new ConcurrentHashMap<>();

    public InMemoryUserRateLimiter(@Value("${chat.rate-limit.per-minute:30}") int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    @Override
    public void checkLimit(Long userId) {
        if (maxRequestsPerMinute <= 0) {
            return;
        }

        long now = Instant.now().toEpochMilli();
        long threshold = now - WINDOW_MILLIS;
        Deque<Long> window = userWindows.computeIfAbsent(userId, ignored -> new ArrayDeque<>());

        synchronized (window) {
            while (!window.isEmpty() && window.peekFirst() < threshold) {
                window.removeFirst();
            }

            if (window.size() >= maxRequestsPerMinute) {
                throw new ApiClientException(
                        HttpStatus.TOO_MANY_REQUESTS,
                        "RATE_LIMIT_EXCEEDED",
                        "Rate limit exceeded"
                );
            }

            window.addLast(now);
        }
    }
}
