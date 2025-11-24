package com.common.base.ratelimit.responseEntity;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RateLimitStats {
    private LocalDateTime firstRequest;
    private LocalDateTime lastRequest;
    private long totalRequests;
    private long blockedRequests;
    private double requestsPerMinute;
}
