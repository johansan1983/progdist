package com.superchat.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimiterConfig {

    /**
     * Per-user rate limiting using the JWT sub claim.
     * Falls back to remote IP for unauthenticated requests.
     */
    @Bean
    public KeyResolver userKeyResolver() {
        return exchange -> exchange.getPrincipal()
                .map(principal -> "user:" + principal.getName())
                .switchIfEmpty(Mono.just("ip:" + exchange.getRequest()
                        .getRemoteAddress()
                        .getAddress()
                        .getHostAddress()));
    }
}
