package com.stylering.ratelimit;

public interface UserRateLimiter {
    void checkLimit(Long userId);
}
