package com.common.base.ratelimit.service;

import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import org.springframework.data.redis.core.RedisTemplate;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class RateLimitingService {

    private final RedisTemplate<String, Object> redisTemplate;

    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    public RateLimitingService(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    // Fixed Window Rate Limiting
    public boolean isAllowedFixedWindow(String key, int maxRequests, int windowSeconds){
        String redisKey = RATE_LIMIT_PREFIX+"fixed:"+key;
        Long currentCount = redisTemplate.opsForValue().increment(redisKey);
        if(currentCount != null && currentCount == 1){
            redisTemplate.expire(redisKey, windowSeconds, TimeUnit.SECONDS);
        }
        return currentCount != null && currentCount <= maxRequests;
    }

    // Sliding Window Rate Limiting
    public boolean isAllowedSlidingWindow(String key, int maxRequest, int windowSeconds){
        String redisKey = RATE_LIMIT_PREFIX+"sliding:"+key;
        long currentTime = System.currentTimeMillis();
        long windowStart = currentTime - (windowSeconds * 1000L);
        // Remove outdated timestamps
        redisTemplate.opsForZSet().removeRangeByScore(redisKey,0, windowStart);

        // Count requests in current window
        Long currentCount = redisTemplate.opsForZSet().count(redisKey, windowStart, currentTime);

        if(currentCount != null && currentCount < maxRequest){
            redisTemplate.opsForZSet().add(redisKey, String.valueOf(currentTime), currentTime);
            redisTemplate.expire(redisKey, windowSeconds, TimeUnit.SECONDS);
            return true;
        }
        return false;
    }

    // Token Bucket Rate Limiting using Bucket4j
    public boolean isAllowedTokenBucket(String key, int capacity, int refillTokens, int refillSeconds){
        String redisKey = RATE_LIMIT_PREFIX+"token:"+key;
        // Get or create bucket
        Bucket bucket = getBucket(redisKey, capacity, refillTokens, refillSeconds);
        return bucket.tryConsume(1);
    }

    private Bucket getBucket(String key, int capacity, int refillTokens, int refillSeconds){
        Refill refill = Refill.intervally(refillTokens, Duration.ofSeconds(refillSeconds));
        Bandwidth limit = Bandwidth.classic(capacity, refill);
        return Bucket4j.builder()
                .addLimit(limit)
                .build();
    }

}
