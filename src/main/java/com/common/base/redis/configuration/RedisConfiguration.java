package com.common.base.redis.configuration;

import io.github.bucket4j.distributed.ExpirationAfterWriteStrategy;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.redis.lettuce.cas.LettuceBasedProxyManager;
import io.lettuce.core.RedisClient;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.ByteArrayCodec;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.codec.StringCodec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.JdkSerializationRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;

@Configuration
@Slf4j
// Only load this configuration if the Bucket4j class is present AND
// if a property is explicitly set (e.g., 'rate-limiting.enabled=true')
@ConditionalOnClass(LettuceBasedProxyManager.class)
@ConditionalOnProperty(prefix = "spring.redis", name = "host") // Simple check to ensure basic config exists
public class RedisConfiguration {

    @Value("${spring.redis.host}")
    private String redisHost;

    @Value("${spring.redis.port}")
    private int redisPort;

    @Bean
    public LettuceConnectionFactory lettuceConnectionFactory(){
        log.info("Configuring Redis connection for {}:{}", redisHost, redisPort);
        RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(redisHost, redisPort);
        return new LettuceConnectionFactory(config);
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory){
        RedisTemplate<String, Object> redisTemplate = new RedisTemplate<>();
        redisTemplate.setConnectionFactory(connectionFactory);
        redisTemplate.setKeySerializer(new StringRedisSerializer());
        redisTemplate.setHashKeySerializer(new StringRedisSerializer());
        redisTemplate.setValueSerializer(new JdkSerializationRedisSerializer());
        redisTemplate.setHashValueSerializer(new JdkSerializationRedisSerializer());
        redisTemplate.afterPropertiesSet();
        return redisTemplate;
    }


    /**
     * 1. PROVIDE THE NATIVE RedisClient INSTANCE
     * This client instance is managed by Spring Data Redis's LettuceConnectionFactory
     * and is the required dependency for Bucket4j's LettuceBasedProxyManager.
     * * @param connectionFactory The Spring Data Redis connection factory.
     * @return The native Lettuce RedisClient instance.
     */
    @Bean
    public RedisClient redisClient(RedisConnectionFactory connectionFactory) {
        if (!(connectionFactory instanceof LettuceConnectionFactory lettuceFactory)) {
            // Throw a meaningful exception if the configuration is incorrect.
            throw new IllegalStateException("RedisConnectionFactory is not a LettuceConnectionFactory. Bucket4j Redis integration requires Lettuce.");
        }

        // Extract the native RedisClient instance
        Object nativeClient = lettuceFactory.getNativeClient();
        if (nativeClient instanceof RedisClient client) {
            return client;
        }

        throw new IllegalStateException("Could not extract RedisClient from LettuceConnectionFactory. Check Lettuce setup.");
    }

    @Bean
    public StatefulRedisConnection<String, byte[]> stringKeyConnection(RedisClient redisClient) {
        // Define the codec: Key (String) -> StringCodec.UTF8, Value (Bucket data) -> ByteArrayCodec.INSTANCE
        RedisCodec<String, byte[]> codec = RedisCodec.of(StringCodec.UTF8, ByteArrayCodec.INSTANCE);
        return redisClient.connect(codec);
    }

    /**
     * 2. CREATE THE BUCKET4J PROXY MANAGER
     * This uses the RedisClient instance to manage rate-limiting state via Redis's CAS operations.
     * The LettuceBasedProxyManager handles connection creation/management internally.
     * * @param redisClient The native Lettuce RedisClient bean.
     * @return The Bucket4j ProxyManager for distributed rate limiting.
     */
    @Bean
    public ProxyManager<String> proxyManager(StatefulRedisConnection<String, byte[]> stringKeyConnection) {
        // The builder will correctly infer ProxyManager<String> because of the connection type.
        // Define the expiration strategy using the configured TTL

        //TODO: NOTES
        /**
         * When using a distributed storage (like Redis), Bucket4j needs to know when to delete the
         * bucket keys that haven't been used in a while, so they don't clog up your database forever.
         *
         *
         *
         *
         * Why basedOnTimeForRefillingBucketUpToMax?
         * Imagine you have a bucket that allows 100 requests per hour.
         *
         * A user makes 5 requests.
         *
         * The bucket is now nearly empty (95 requests remaining to refill).
         *
         * If you set a fixed TTL of 10 minutes, and the user waits 11 minutes, the Redis key could expire and be deleted.
         *
         * When the user makes the next request, Bucket4j would see no key, assume a brand new bucket, and give the user 100 requests again, effectively resetting the rate limit early.
         */
        ExpirationAfterWriteStrategy expirationStrategy =
                ExpirationAfterWriteStrategy.basedOnTimeForRefillingBucketUpToMax(Duration.ofMinutes(60));
        try {
            return LettuceBasedProxyManager.builderFor(stringKeyConnection)
                    .withExpirationStrategy(expirationStrategy)
                    .build();
        } catch (Exception e) {
            log.error("Failed to create Bucket4j ProxyManager. Rate limiting will not function.", e);
            throw new IllegalStateException("Failed to create Bucket4j ProxyManager.", e);
        }
    }
}