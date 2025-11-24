package com.common.base.ratelimit.responseEntity;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.io.Serializable;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Data
@AllArgsConstructor
public class BucketRateLimitInfo implements Serializable {

    private boolean allowed;

    private long remainingEstimate;

    private long nanosToWaitForRefill;

    public Duration getWaitDuration() {
        return Duration.ofNanos(nanosToWaitForRefill);
    }

    public long getWaitSeconds() {
        return TimeUnit.NANOSECONDS.toSeconds(nanosToWaitForRefill);
    }
}
