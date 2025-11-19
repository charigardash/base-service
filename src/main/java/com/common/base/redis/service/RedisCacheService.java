package com.common.base.redis.service;

import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

@Service
public class RedisCacheService {

    private final CacheManager cacheManager;

    private final RedisTemplate<String, Object> redisTemplate;

    public RedisCacheService(CacheManager cacheManager, RedisTemplate<String, Object> redisTemplate) {
        this.cacheManager = cacheManager;
        this.redisTemplate = redisTemplate;
    }

    public void put(String cacheName, String key, Object value){
        Cache cache = cacheManager.getCache(cacheName);
        if(cache != null)cache.put(key, value);
    }

    public <T> T get(String cacheName, String key, Class<T> type){
        Cache cache = cacheManager.getCache(cacheName);
        if(cache != null){
            Cache.ValueWrapper valueWrapper = cache.get(key);
            if(valueWrapper != null){
                type.cast(valueWrapper);
            }
        }
        return null;
    }

    public void evict(String cacheName, String key){
        Cache cache = cacheManager.getCache(cacheName);
        if(cache != null){
            cache.evict(key);
        }
    }

    public void evictAll(String cacheName){
        Cache cache = cacheManager.getCache(cacheName);
        if(cache != null){
            cache.clear();
        }
    }

    public boolean exists(String cacheName, String key){
        Cache cache = cacheManager.getCache(cacheName);
        return cache != null && cache.get(key) != null;
    }

    public void putWithCustomTtl(String cacheName, String key, Object value, long ttl, TimeUnit timeUnit){
        redisTemplate.opsForValue().set(buildKey(cacheName, key), value, ttl, timeUnit);
    }

    private String buildKey(String cacheName, String key) {
        return cacheName+"::"+key;
    }
}
