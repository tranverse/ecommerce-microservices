package com.example.ecommerce.gateway.web;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.util.UUID;
import java.util.regex.Pattern;

@Component
public class CorrelationIdWebFilter implements WebFilter, Ordered {

    public static final String HEADER_NAME = "X-Correlation-ID";
    public static final String ATTRIBUTE_NAME = CorrelationIdWebFilter.class.getName() + ".correlationId";
    private static final Pattern SAFE_VALUE = Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String correlationId = correlationId(exchange.getRequest().getHeaders().getFirst(HEADER_NAME));
        ServerHttpRequest request = exchange.getRequest().mutate()
                .headers(headers -> headers.set(HEADER_NAME, correlationId))
                .build();
        ServerWebExchange mutatedExchange = exchange.mutate().request(request).build();
        mutatedExchange.getAttributes().put(ATTRIBUTE_NAME, correlationId);
        mutatedExchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);
        mutatedExchange.getResponse().beforeCommit(() -> {
            mutatedExchange.getResponse().getHeaders().set(HEADER_NAME, correlationId);
            return Mono.empty();
        });
        return chain.filter(mutatedExchange)
                .contextWrite(context -> context.put(ATTRIBUTE_NAME, correlationId));
    }

    private String correlationId(String candidate) {
        if (candidate == null || !SAFE_VALUE.matcher(candidate).matches()) {
            return UUID.randomUUID().toString();
        }
        return candidate;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
