package com.common.base.redis.service;

import com.common.base.redis.annotation.CacheEvictRedis;
import com.common.base.redis.annotation.CacheableRedis;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.aspectj.lang.annotation.Aspect;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

@Aspect
@Component
public class CacheAspect {

    private final RedisCacheService redisCacheService;

    private final ExpressionParser parser = new SpelExpressionParser();

    public CacheAspect(RedisCacheService redisCacheService) {
        this.redisCacheService = redisCacheService;
    }

    @Around("@annotation(cacheableRedis)")
    public Object handleCacheable(ProceedingJoinPoint joinPoint, CacheableRedis cacheableRedis) throws Throwable {
        String cacheName = cacheableRedis.cacheName();
        String key = resolveKey(joinPoint, cacheableRedis.key());

        // Try to get from cache first
        Object cachedValue = redisCacheService.get(cacheName, key, Object.class);
        if(cachedValue != null)return cachedValue;

        // Execute method if not cached
        Object result = joinPoint.proceed();

        // Cache the result
        if(result != null){
            if(cacheableRedis.ttl()>0){
                redisCacheService.putWithCustomTtl(cacheName, key, result, cacheableRedis.ttl(), TimeUnit.SECONDS);
            } else {
                redisCacheService.put(cacheName, key, result);
            }
        }
        return result;
    }

    @Around("@annotation(cacheEvictRedis)")
    public Object handleCacheEvict(ProceedingJoinPoint joinPoint, CacheEvictRedis cacheEvictRedis) throws Throwable {
        String cacheName = cacheEvictRedis.cacheName();

        // Execute the method first
        Object result = joinPoint.proceed();

        // Then evict cache
        if(cacheEvictRedis.allEntries()){
            redisCacheService.evictAll(cacheName);
        } else{
            String key = resolveKey(joinPoint, cacheEvictRedis.key());
            redisCacheService.evict(cacheName, key);
        }
        return result;
    }

    private String resolveKey(ProceedingJoinPoint joinPoint, String keyExpression){
        if(keyExpression.isEmpty()){
            Method method = ((MethodSignature)joinPoint.getSignature()).getMethod();
            return method.getName();
        }
        // Support SpEL expressions for dynamic keys
        StandardEvaluationContext context = new StandardEvaluationContext();
        Object[] args = joinPoint.getArgs();
        String[] parameterNames = ((MethodSignature)joinPoint.getSignature()).getParameterNames();
        for(int i=0;i< parameterNames.length;i++){
            context.setVariable(parameterNames[i], args[i]);
        }
        return parser.parseExpression(keyExpression).getValue(context, String.class);
    }
}
