package com.example.ecommerce.gateway.web;

import org.junit.jupiter.api.Test;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CorrelationIdWebFilterTest {

    private final CorrelationIdWebFilter filter = new CorrelationIdWebFilter();

    @Test
    void preservesASafeCallerCorrelationId() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                        .header(CorrelationIdWebFilter.HEADER_NAME, "client-request_123")
                        .build()
        );
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, filtered -> {
                    forwarded.set(filtered);
                    return Mono.empty();
                }))
                .verifyComplete();

        assertThat(forwarded.get().getRequest().getHeaders().getFirst(CorrelationIdWebFilter.HEADER_NAME))
                .isEqualTo("client-request_123");
        assertThat(exchange.getResponse().getHeaders().getFirst(CorrelationIdWebFilter.HEADER_NAME))
                .isEqualTo("client-request_123");
    }

    @Test
    void replacesAnUnsafeCorrelationId() {
        ServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/test")
                        .header(CorrelationIdWebFilter.HEADER_NAME, "../../ unsafe")
                        .build()
        );
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, filtered -> {
                    forwarded.set(filtered);
                    return Mono.empty();
                }))
                .verifyComplete();

        String generated = forwarded.get().getRequest().getHeaders()
                .getFirst(CorrelationIdWebFilter.HEADER_NAME);
        assertThat(UUID.fromString(generated)).isNotNull();
    }

    @Test
    void generatesAnIdWhenTheHeaderIsAbsent() {
        ServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/test").build());
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        StepVerifier.create(filter.filter(exchange, filtered -> {
                    forwarded.set(filtered);
                    return Mono.empty();
                }))
                .verifyComplete();

        String generated = forwarded.get().getAttribute(CorrelationIdWebFilter.ATTRIBUTE_NAME);
        assertThat(UUID.fromString(generated)).isNotNull();
    }
}
