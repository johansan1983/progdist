package com.superchat.gateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.concurrent.atomic.AtomicLong;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);
    public static final String REQUEST_ID_HEADER = "X-Request-ID";
    private static final AtomicLong COUNTER = new AtomicLong(0);

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String requestId = exchange.getRequest().getHeaders().getFirst(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            requestId = String.format("%04d", COUNTER.incrementAndGet());
        }
        final String rid = requestId;

        String path = exchange.getRequest().getPath().value();
        if (!path.startsWith("/actuator") && !path.equals("/api/chat/presence")) {
            MDC.put("requestId", rid);
            log.info("[API] {} {}", exchange.getRequest().getMethod(), path);
            MDC.remove("requestId");
        }

        ServerWebExchange mutated = exchange.mutate()
                .request(r -> r.header(REQUEST_ID_HEADER, rid))
                .build();

        // Echo the ID on the response so nginx (and any client) can log it.
        mutated.getResponse().beforeCommit(() -> {
            mutated.getResponse().getHeaders().set(REQUEST_ID_HEADER, rid);
            return Mono.empty();
        });

        // contextWrite propagates requestId through the reactive chain;
        // MdcContextLifterConfig.MdcLifter restores it into MDC at each async boundary.
        return chain.filter(mutated)
                .contextWrite(ctx -> ctx.put("requestId", rid));
    }
}
