package com.common.base.redis.configuration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties(prefix = "redis.cache")
public class RedisCacheProperties {

    private long defaultTtl = 3600;

    private String keyPrefix = "app";

    private boolean cacheNullValues = false;

    private boolean enableStatistics = false;

    // Getters and setters
    public long getDefaultTtl() {
        return defaultTtl;
    }

    public void setDefaultTtl(long defaultTtl) {
        this.defaultTtl = defaultTtl;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public void setKeyPrefix(String keyPrefix) {
        this.keyPrefix = keyPrefix;
    }

    public boolean isCacheNullValues() {
        return cacheNullValues;
    }

    public void setCacheNullValues(boolean cacheNullValues) {
        this.cacheNullValues = cacheNullValues;
    }

    public boolean isEnableStatistics() {
        return enableStatistics;
    }

    public void setEnableStatistics(boolean enableStatistics) {
        this.enableStatistics = enableStatistics;
    }

}
