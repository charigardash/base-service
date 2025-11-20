package com.common.base.ratelimit.exception;

public class RateLimitExceededException extends RuntimeException{

    public RateLimitExceededException(String message){
        super(message);
    }
}
