package com.example.ecommerce.order.config;

import com.example.ecommerce.order.web.CorrelationIdFilter;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.retry.RetryRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

@Configuration(proxyBeanMethods = false)
public class ProductCatalogClientConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogClientConfiguration.class);
    static final String RESILIENCE_INSTANCE_NAME = "productCatalog";

    @Bean("productCatalogRestClient")
    RestClient productCatalogRestClient(RestClient.Builder builder, ProductCatalogProperties properties) {
        return configureProductCatalogClient(builder, properties).build();
    }

    RestClient.Builder configureProductCatalogClient(
            RestClient.Builder builder,
            ProductCatalogProperties properties
    ) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.connectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());

        return builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .requestInterceptor((request, body, execution) -> {
                    String correlationId = MDC.get("correlationId");
                    if (correlationId != null) {
                        request.getHeaders().set(CorrelationIdFilter.HEADER_NAME, correlationId);
                    }
                    return execution.execute(request, body);
                });
    }

    @Bean("productCatalogRetry")
    Retry productCatalogRetry(RetryRegistry registry, ProductCatalogProperties properties) {
        ProductCatalogProperties.Resilience policy = properties.resilience();
        RetryConfig config = RetryConfig.custom()
                .maxAttempts(policy.maxAttempts())
                .waitDuration(policy.retryWaitDuration())
                .retryOnException(ProductCatalogClientConfiguration::isRetryableFailure)
                .build();
        Retry retry = registry.retry(RESILIENCE_INSTANCE_NAME, config);
        retry.getEventPublisher().onRetry(event -> log.warn(
                "Retrying product catalog lookup attempt={} failureType={}",
                event.getNumberOfRetryAttempts(),
                event.getLastThrowable().getClass().getSimpleName()
        ));
        return retry;
    }

    @Bean("productCatalogCircuitBreaker")
    CircuitBreaker productCatalogCircuitBreaker(
            CircuitBreakerRegistry registry,
            ProductCatalogProperties properties
    ) {
        ProductCatalogProperties.Resilience policy = properties.resilience();
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(policy.failureRateThreshold())
                .slidingWindowType(CircuitBreakerConfig.SlidingWindowType.COUNT_BASED)
                .slidingWindowSize(policy.slidingWindowSize())
                .minimumNumberOfCalls(policy.minimumNumberOfCalls())
                .permittedNumberOfCallsInHalfOpenState(policy.permittedCallsInHalfOpenState())
                .waitDurationInOpenState(policy.openStateWaitDuration())
                .recordException(ProductCatalogClientConfiguration::isCircuitBreakerFailure)
                .ignoreException(HttpClientErrorException.class::isInstance)
                .build();
        CircuitBreaker circuitBreaker = registry.circuitBreaker(RESILIENCE_INSTANCE_NAME, config);
        circuitBreaker.getEventPublisher().onStateTransition(event -> log.warn(
                "Product catalog circuit breaker transition={}",
                event.getStateTransition()
        ));
        return circuitBreaker;
    }

    private static boolean isRetryableFailure(Throwable throwable) {
        return throwable instanceof ResourceAccessException
                || throwable instanceof HttpServerErrorException;
    }

    private static boolean isCircuitBreakerFailure(Throwable throwable) {
        return !(throwable instanceof HttpClientErrorException);
    }
}
