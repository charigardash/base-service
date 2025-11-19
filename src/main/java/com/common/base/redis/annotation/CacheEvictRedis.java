package com.common.base.redis.annotation;

import java.lang.annotation.*;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface CacheEvictRedis {
    String cacheName();
    String key() default "";
    boolean allEntries() default false;
}
