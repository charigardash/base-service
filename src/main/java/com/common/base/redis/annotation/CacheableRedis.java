package com.common.base.redis.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheableRedis {
    String cacheName();
    String key() default "";
    long ttl() default -1;
}
