package com.example.ecommerce.order.client;

import com.example.ecommerce.order.exception.ProductCatalogUnavailableException;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

@Component
public class RestProductCatalogClient implements ProductCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(RestProductCatalogClient.class);

    private final RestClient restClient;
    private final Retry retry;
    private final CircuitBreaker circuitBreaker;

    public RestProductCatalogClient(
            @Qualifier("productCatalogRestClient") RestClient restClient,
            @Qualifier("productCatalogRetry") Retry retry,
            @Qualifier("productCatalogCircuitBreaker") CircuitBreaker circuitBreaker
    ) {
        this.restClient = restClient;
        this.retry = retry;
        this.circuitBreaker = circuitBreaker;
    }

    @Override
    public List<CatalogProductResponse> getProducts(Set<UUID> productIds) {
        try {
            Supplier<CatalogProductResponse[]> retryableLookup = Retry.decorateSupplier(
                    retry,
                    () -> fetchProducts(productIds)
            );
            CatalogProductResponse[] response = CircuitBreaker.decorateSupplier(
                    circuitBreaker,
                    retryableLookup
            ).get();
            return List.copyOf(Arrays.asList(response));
        } catch (CallNotPermittedException exception) {
            log.warn("Product catalog lookup rejected circuitState={}", circuitBreaker.getState());
            throw new ProductCatalogUnavailableException();
        } catch (ProductCatalogUnavailableException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn(
                    "Product catalog lookup failed type={} circuitState={}",
                    exception.getClass().getSimpleName(),
                    circuitBreaker.getState()
            );
            throw new ProductCatalogUnavailableException();
        }
    }

    private CatalogProductResponse[] fetchProducts(Set<UUID> productIds) {
        CatalogProductResponse[] response = restClient.get()
                .uri(uriBuilder -> {
                    uriBuilder.path("/api/v1/products/batch");
                    productIds.stream()
                            .sorted(Comparator.naturalOrder())
                            .forEach(productId -> uriBuilder.queryParam("ids", productId));
                    return uriBuilder.build();
                })
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(CatalogProductResponse[].class);
        if (response == null || Arrays.stream(response).anyMatch(Objects::isNull)) {
            throw new ProductCatalogUnavailableException();
        }
        return response;
    }
}
