package com.common.base.ratelimit.service;

import com.common.base.ratelimit.responseEntity.BucketRateLimitInfo;
import com.common.base.ratelimit.responseEntity.RateLimitStats;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.Bucket4j;
import io.github.bucket4j.Refill;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;

import java.time.Duration;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class RateLimitingService {

    private final RedisTemplate<String, Object> redisTemplate;

    private final TokenBucketRateLimiter tokenBucketRateLimiter;

    private static final String RATE_LIMIT_PREFIX = "rate_limit:";

    // Rate limit types
    public static final String RATE_LIMIT_OTP_EMAIL = "OTP_EMAIL";
    public static final String RATE_LIMIT_OTP_SMS = "OTP_SMS";
    public static final String RATE_LIMIT_LOGIN = "LOGIN_ATTEMPTS";
    public static final String RATE_LIMIT_API = "API_GENERAL";

    public RateLimitingService(RedisTemplate<String, Object> redisTemplate, TokenBucketRateLimiter tokenBucketRateLimiter) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketRateLimiter = tokenBucketRateLimiter;
    }

    // Token Bucket based rate limiting (Primary method)
    public RateLimitResult checkRateLimit(String identifier, String rateLimitType){
        String key = buildRedisKey(identifier, rateLimitType);
        BucketRateLimitInfo rateLimitInfo = tokenBucketRateLimiter.getRateLimitInfo(key, rateLimitType);
        return new RateLimitResult(rateLimitInfo.isAllowed(), rateLimitInfo.getRemainingEstimate(), rateLimitInfo.getWaitSeconds(), "TokenBucket");
    }

    private String buildRedisKey(String identifier, String rateLimitType){
        return String.format("rate_limit:%s:%s", rateLimitType, DigestUtils.md5DigestAsHex(identifier.getBytes()));
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

    public RateLimitResult checkCompositeRateLimit(String identifier, String rateLimitType){
        // First check token bucket
        RateLimitResult tokenBucketResult = checkRateLimit(identifier, rateLimitType);

        if (!tokenBucketResult.isAllowed()) {
            log.warn("Rate limit exceeded for {} using TokenBucket. Identifier: {}",
                    rateLimitType, identifier);
            return tokenBucketResult;
        }

        // Additional checks can be added here
        // For example, you might want to check fixed window as a secondary measure

        return tokenBucketResult;
    }

    // Reset rate limit for a specific identifier
    public void resetRateLimit(String identifier, String rateLimitType){
        try {
            String pattern = buildRedisKey(identifier, rateLimitType);
            Set<String> keys = redisTemplate.keys(pattern);
            if(!keys.isEmpty()){
                redisTemplate.delete(keys);
                log.info("Reset rate limit for identifier: {}, type: {}", identifier, rateLimitType);
            }
        } catch (Exception e) {
            log.error("Failed to reset rate limit for identifier: {}", identifier, e);
        }
    }

    // Get rate limit statistics
    public RateLimitStats getRateLimitStats(String identifier, String rateLimitType){
        String key = buildRedisKey(identifier, rateLimitType);
        try {
            //TODO Implementation to gather statistics
            return new RateLimitStats();
        } catch (Exception e) {
            log.error("Failed to get rate limit stats for identifier: {}", identifier, e);
            return new RateLimitStats();
        }
    }

    @Data
    @AllArgsConstructor
    public static class RateLimitResult{
        private boolean allowed;
        private long remainingRequests;
        private long waitTimeSeconds;
        private String algorithm;

        public boolean isRateLimited(){
            return !allowed;
        }
    }

}
