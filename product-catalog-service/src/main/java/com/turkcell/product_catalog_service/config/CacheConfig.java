package com.turkcell.product_catalog_service.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.turkcell.product_catalog_service.dto.TariffResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;

/**
 * Dokuman 8.2: product-catalog read-heavy bir servistir, Redis cache-aside kullanilir.
 * Cache sadece hot-path olan getByCode uzerinde (Order Service her sipariste cagirir).
 *
 * Redis erisilemezse cache HATA FIRLATMAZ: asagidaki CacheErrorHandler hatayi loglayip
 * yutar, cagri dogrudan DB'ye duser — cache bir hizlandirmadir, bagimlilik degildir.
 */
@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    private static final Logger log = LoggerFactory.getLogger(CacheConfig.class);

    public static final String TARIFFS_CACHE = "tariffs";

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        // Cache'te yalnizca TariffResponse tutuldugu icin tipe bagli serializer kullanilir.
        // JavaTimeModule sart: TariffResponse'taki LocalDate alanlari (effectiveFrom/To)
        // varsayilan ObjectMapper ile serialize edilemez ve cache sessizce devre disi kalir.
        ObjectMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .build();
        Jackson2JsonRedisSerializer<TariffResponse> serializer =
                new Jackson2JsonRedisSerializer<>(mapper, TariffResponse.class);

        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10))
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(serializer));
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            @Override
            public void handleCacheGetError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache GET hatasi (cache={}, key={}): {} — DB'ye dusuluyor", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCachePutError(RuntimeException e, Cache cache, Object key, Object value) {
                log.warn("Cache PUT hatasi (cache={}, key={}): {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheEvictError(RuntimeException e, Cache cache, Object key) {
                log.warn("Cache EVICT hatasi (cache={}, key={}): {}", cache.getName(), key, e.getMessage());
            }

            @Override
            public void handleCacheClearError(RuntimeException e, Cache cache) {
                log.warn("Cache CLEAR hatasi (cache={}): {}", cache.getName(), e.getMessage());
            }
        };
    }
}
