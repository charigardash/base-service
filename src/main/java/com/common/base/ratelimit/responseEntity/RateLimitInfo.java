package com.common.base.ratelimit.responseEntity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
public class RateLimitInfo implements Serializable {

    private LocalDateTime firstAttempt = LocalDateTime.now();

    private int attemptCount = 0;

    private LocalDateTime lastAttempt;

    private boolean blocked = false;

    public void recordAttempt(boolean success){
        this.attemptCount++;
        this.lastAttempt = LocalDateTime.now();

        //Reset if successful after previous failure
        if(success && this.attemptCount > 0){
            this.attemptCount = 0;
            this.blocked = false;
        }
    }


}
