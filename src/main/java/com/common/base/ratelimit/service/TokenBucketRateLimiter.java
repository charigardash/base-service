package com.common.base.ratelimit.service;

import com.common.base.ratelimit.configuration.RedisBucketProperties;
import com.common.base.ratelimit.responseEntity.BucketRateLimitInfo;
import io.github.bucket4j.*;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
@Slf4j
public class TokenBucketRateLimiter {

    private final ProxyManager<String> proxyManager;

    private final RedisBucketProperties redisBucketProperties;

    private final Map<String, BucketConfiguration> bucketConfigs;

    public TokenBucketRateLimiter(ProxyManager<String> proxyManager, RedisBucketProperties redisBucketProperties) {
        this.proxyManager = proxyManager;
        this.redisBucketProperties = redisBucketProperties;
        this.bucketConfigs = initializeBucketConfiguration();
    }

    private Map<String, BucketConfiguration> initializeBucketConfiguration(){
        Map<String, BucketConfiguration> configs = new ConcurrentHashMap<>();

        // OTP Email Configuration
        configs.put("OTP_EMAIL", BucketConfiguration.builder()
                        .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(5))))
                        .build());

        // OTP SMS Configuration
        configs.put("OTP_SMS", BucketConfiguration.builder()
                        .addLimit(Bandwidth.classic(3, Refill.intervally(3, Duration.ofMinutes(5))))
                .build());

        // API Configuration
        configs.put("API_GENERAL", BucketConfiguration.builder()
                        .addLimit(Bandwidth.classic(100, Refill.intervally(100, Duration.ofHours(1))))
                .build());

        // Login Attempts Configuration
        configs.put("LOGIN_ATTEMPTS", BucketConfiguration.builder()
                        .addLimit(Bandwidth.classic(5, Refill.intervally(5, Duration.ofMinutes(15))))
                .build());

        return configs;
    }

    public ConsumptionProbe tryConsume(String key, String bucketType){
        try {
            BucketConfiguration config = bucketConfigs.getOrDefault(bucketType, getDefaultConfiguration());
            /**
             * key → a unique identifier for the bucket (e.g. "user:123", "ip:10.0.0.1").
             * config → a BucketConfiguration object that defines the rate limits (bandwidths, refill rates, etc).
             * This call either creates the bucket in Redis (if it doesn’t exist yet) or loads the existing one with the given key.
             */
            Bucket bucket = proxyManager.builder().build(key, config);
            return bucket.tryConsumeAndReturnRemaining(1);
        }catch (Exception e){
            log.error("Error checking token bucket for key: {}, type: {}", key, bucketType, e);
            //TODO: Fail open - allow request in case of Redis failure
            throw e;
        }
    }

    public ConsumptionProbe tryConsume(String key, String bucketType, int tokens){
        try {
            BucketConfiguration config = bucketConfigs.getOrDefault(bucketType, getDefaultConfiguration());
            Bucket bucket = proxyManager.builder().build(key, config);
            return bucket.tryConsumeAndReturnRemaining(tokens);
        } catch (Exception e) {
            log.error("Error checking token bucket for key: {}, type: {}, tokens: {}",
                    key, bucketType, tokens, e);
            //TODO: Fail open - allow request in case of Redis failure
            throw e;
        }
    }

    public EstimationProbe estimateAbilityToConsume(String key, String bucketType, int tokens){
        try {
            BucketConfiguration config = bucketConfigs.getOrDefault(bucketType, getDefaultConfiguration());
            Bucket bucket = proxyManager.builder().build(key, config);
            return bucket.estimateAbilityToConsume(tokens);
        } catch (Exception e) {
            log.error("Error estimating token consumption for key: {}, type: {}, tokens: {}",
                    key, bucketType, tokens, e);
            // Return optimistic estimation in case of failure
            return EstimationProbe.canBeConsumed(tokens);
        }
    }

    public void addBucketConfiguration(String bucketType, BucketConfiguration bucketConfiguration){
        bucketConfigs.put(bucketType, bucketConfiguration);
        log.info("Added new bucket configuration for type: {}", bucketType);
    }

    public void removeBucketConfiguration(String bucketType){
        bucketConfigs.remove(bucketType);
        log.info("Removed bucket configuration for type: {}", bucketType);
    }

    public Map<String, BucketConfiguration> getBucketConfigurations(){
        return new HashMap<>(bucketConfigs);
    }

    public BucketConfiguration getBucketConfiguration(String bucketType){
        return bucketConfigs.get(bucketType);
    }

    private BucketConfiguration getDefaultConfiguration(){
        return BucketConfiguration.builder()
                .addLimit(Bandwidth.classic(redisBucketProperties.getDefaultCapacity(),
                        Refill.intervally(redisBucketProperties.getDefaultRefillTokens(),
                                Duration.ofMinutes(redisBucketProperties.getDefaultRefillMinutes()))))
                .build();
    }

    // Utility methods for common scenarios
    public boolean isAllowed(String key, String bucketType){
        return tryConsume(key, bucketType).isConsumed();
    }

    public BucketRateLimitInfo getRateLimitInfo(String key, String bucketType){
        ConsumptionProbe probe = tryConsume(key, bucketType);
        return new BucketRateLimitInfo(probe.isConsumed(), probe.getRemainingTokens(), probe.getNanosToWaitForRefill());
    }


}
