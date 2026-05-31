package com.superchat.moderation.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * Caffeine cache with a 60-second TTL for org word-lists.
 *
 * Why a TTL matters: @CacheEvict on add/delete only clears the LOCAL JVM cache. With more
 * than one moderation-service replica, other instances would otherwise serve a stale word
 * list indefinitely (a deleted block rule could keep blocking, a new one wouldn't apply).
 * A 60s expiry bounds cross-replica staleness so every instance converges within a minute,
 * while still absorbing the per-message read load.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager("moderationRules");
        manager.setCaffeine(Caffeine.newBuilder()
                .expireAfterWrite(60, TimeUnit.SECONDS)
                .maximumSize(10_000));
        return manager;
    }
}
