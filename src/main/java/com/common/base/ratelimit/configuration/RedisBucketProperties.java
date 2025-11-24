package com.common.base.ratelimit.configuration;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@ConfigurationProperties("base.rate-limit.token-bucket")
@Data
@Configuration
public class RedisBucketProperties {
    private int defaultCapacity = 10;
    private int defaultRefillTokens = 5;
    private int defaultRefillMinutes = 1;
    private boolean enabled = true;
}
