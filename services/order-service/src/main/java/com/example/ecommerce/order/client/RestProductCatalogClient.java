package com.example.ecommerce.order.client;

import com.example.ecommerce.order.exception.ProductCatalogUnavailableException;
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
import java.util.Set;
import java.util.UUID;

@Component
public class RestProductCatalogClient implements ProductCatalogClient {

    private static final Logger log = LoggerFactory.getLogger(RestProductCatalogClient.class);

    private final RestClient restClient;

    public RestProductCatalogClient(@Qualifier("productCatalogRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<CatalogProductResponse> getProducts(Set<UUID> productIds) {
        try {
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
            if (response == null) {
                throw new ProductCatalogUnavailableException();
            }
            return List.copyOf(Arrays.asList(response));
        } catch (ProductCatalogUnavailableException exception) {
            throw exception;
        } catch (RestClientException exception) {
            log.warn("Product catalog lookup failed type={}", exception.getClass().getSimpleName());
            throw new ProductCatalogUnavailableException();
        }
    }
}
