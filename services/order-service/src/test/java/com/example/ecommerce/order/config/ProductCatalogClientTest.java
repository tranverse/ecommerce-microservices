package com.example.ecommerce.order.config;

import com.example.ecommerce.order.client.CatalogProductResponse;
import com.example.ecommerce.order.client.RestProductCatalogClient;
import com.example.ecommerce.order.exception.ProductCatalogUnavailableException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class ProductCatalogClientTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void sendsOneSortedBatchRequestAndPropagatesCorrelationId() {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        RestClient.Builder builder = RestClient.builder();
        TestClient testClient = client(builder);
        MockRestServiceServer server = testClient.server();
        RestProductCatalogClient client = testClient.client();
        MDC.put("correlationId", "order-client-test");
        server.expect(once(), requestTo(
                        "http://product.test/api/v1/products/batch?ids=" + firstId + "&ids=" + secondId
                ))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("X-Correlation-ID", "order-client-test"))
                .andRespond(withSuccess("""
                        [{
                          "id":"00000000-0000-0000-0000-000000000001",
                          "sku":"SKU-001",
                          "name":"Product One",
                          "price":10.00,
                          "currency":"USD",
                          "status":"ACTIVE"
                        }]
                        """, MediaType.APPLICATION_JSON));

        List<CatalogProductResponse> result = client.getProducts(Set.of(secondId, firstId));

        assertThat(result).singleElement()
                .extracting(CatalogProductResponse::id)
                .isEqualTo(firstId);
        server.verify();
    }

    @Test
    void mapsDownstreamFailureToSanitizedAvailabilityError() {
        UUID productId = UUID.randomUUID();
        RestClient.Builder builder = RestClient.builder();
        TestClient testClient = client(builder);
        MockRestServiceServer server = testClient.server();
        RestProductCatalogClient client = testClient.client();
        server.expect(requestTo("http://product.test/api/v1/products/batch?ids=" + productId))
                .andRespond(withServerError().body("private downstream failure"));

        assertThatThrownBy(() -> client.getProducts(Set.of(productId)))
                .isInstanceOf(ProductCatalogUnavailableException.class)
                .hasMessage("Product catalog is temporarily unavailable");
        server.verify();
    }

    private TestClient client(RestClient.Builder builder) {
        ProductCatalogProperties properties = new ProductCatalogProperties(
                "http://product.test",
                Duration.ofSeconds(2),
                Duration.ofSeconds(3)
        );
        RestClient.Builder configuredBuilder = new ProductCatalogClientConfiguration()
                .configureProductCatalogClient(builder, properties);
        MockRestServiceServer server = MockRestServiceServer.bindTo(configuredBuilder).build();
        RestClient restClient = configuredBuilder.build();
        return new TestClient(new RestProductCatalogClient(restClient), server);
    }

    private record TestClient(RestProductCatalogClient client, MockRestServiceServer server) {
    }
}
