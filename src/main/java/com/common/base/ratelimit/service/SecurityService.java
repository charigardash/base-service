package com.common.base.ratelimit.service;

import com.common.base.ratelimit.enums.RateLimitType;
import com.common.base.ratelimit.responseEntity.RateLimitInfo;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class SecurityService {

    private final RateLimitingService rateLimitingService;

    private final Map<String, RateLimitInfo> rateLimitCache = new ConcurrentHashMap<>();

    @Value("${app.rate-limit.max-attempts:5}")
    private int maxAttempts;

    @Value("${app.rate-limit.window-minutes:15}")
    private int windowMinutes;

    public SecurityService(RateLimitingService rateLimitingService) {
        this.rateLimitingService = rateLimitingService;
    }

    public boolean isRateLimited(String identifier, RateLimitType rateLimitType){
        String key = rateLimitType+":"+identifier;
        // Check using sliding window for more accuracy
        return !rateLimitingService.isAllowedSlidingWindow(key, maxAttempts, windowMinutes*60);
    }

    public void recordAttempt(String identifier, RateLimitType type, boolean success){
        String key = "attempt:"+type+":"+identifier;
        RateLimitInfo info = rateLimitCache.getOrDefault(key, new RateLimitInfo());
        info.recordAttempt(success);
        rateLimitCache.put(key, info);
    }

    public RateLimitInfo getRateLimitInfo(String identifier, RateLimitType type) {
        String key = "attempt:" + type + ":" + identifier;
        return rateLimitCache.getOrDefault(key, new RateLimitInfo());
    }
}
