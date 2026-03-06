package com.stylering.ratelimit;

import com.stylering.common.error.ApiClientException;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

@Component
@Profile("!local & !test")
public class RedisUserRateLimiter implements UserRateLimiter {

    private static final long WINDOW_MILLIS = 60_000L;
    private static final long KEY_TTL_SECONDS = 70L;
    private static final String KEY_PREFIX = "rate:";

    // Atomic sliding window via Lua script:
    // 1. ZADD the current request
    // 2. ZREMRANGEBYSCORE to evict old entries
    // 3. ZCARD to count requests in window
    // 4. EXPIRE to keep key alive
    // Returns the current count (including this request)
    private static final RedisScript<Long> RATE_LIMIT_SCRIPT = RedisScript.of(
            "redis.call('ZADD', KEYS[1], ARGV[1], ARGV[2])\n" +
            "redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', ARGV[3])\n" +
            "local count = redis.call('ZCARD', KEYS[1])\n" +
            "redis.call('EXPIRE', KEYS[1], ARGV[4])\n" +
            "return count",
            Long.class
    );

    private final StringRedisTemplate redisTemplate;
    private final int maxRequestsPerMinute;

    public RedisUserRateLimiter(
            StringRedisTemplate redisTemplate,
            @Value("${chat.rate-limit.per-minute:30}") int maxRequestsPerMinute
    ) {
        this.redisTemplate = redisTemplate;
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    @Override
    public void checkLimit(Long userId) {
        if (maxRequestsPerMinute <= 0) {
            return;
        }

        String key = KEY_PREFIX + userId;
        long now = System.currentTimeMillis();
        long threshold = now - WINDOW_MILLIS;

        Long count = redisTemplate.execute(
                RATE_LIMIT_SCRIPT,
                List.of(key),
                String.valueOf(now),
                UUID.randomUUID().toString(),
                String.valueOf(threshold),
                String.valueOf(KEY_TTL_SECONDS)
        );

        if (count != null && count > maxRequestsPerMinute) {
            throw new ApiClientException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "RATE_LIMIT_EXCEEDED",
                    "Rate limit exceeded"
            );
        }
    }
}
