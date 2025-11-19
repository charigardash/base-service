package com.common.base.redis.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@EnableCaching
@EnableConfigurationProperties(RedisCacheProperties.class)
public class RedisCacheConfig {

    private final RedisCacheProperties redisCacheProperties;

    public RedisCacheConfig(RedisCacheProperties redisCacheProperties) {
        this.redisCacheProperties = redisCacheProperties;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory redisConnectionFactory, ObjectMapper objectMapper){
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .prefixCacheNameWith(redisCacheProperties.getKeyPrefix()+":")
                .entryTtl(Duration.ofSeconds(redisCacheProperties.getDefaultTtl()))
                .disableCachingNullValues()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(createRedisSerializer(objectMapper)));
        return RedisCacheManager.builder(redisConnectionFactory)
                .cacheDefaults(config)
                .transactionAware()
                .build();

    }

    private GenericJackson2JsonRedisSerializer createRedisSerializer(ObjectMapper objectMapper) {
        ObjectMapper copy = objectMapper.copy();
        copy.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,//Using LaissezFaireSubTypeValidator can expose your app to remote code execution (RCE) if untrusted data is deserialized. This is especially dangerous in APIs or services that accept JSON from external sources.
                ObjectMapper.DefaultTyping.NON_FINAL,
                JsonTypeInfo.As.PROPERTY
        );
        return new GenericJackson2JsonRedisSerializer(copy);
    }
}
