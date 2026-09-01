package com.example.ecommerce.order.config;

import com.example.ecommerce.order.client.CatalogProductResponse;
import com.example.ecommerce.order.client.RestProductCatalogClient;
import com.example.ecommerce.order.exception.ProductCatalogUnavailableException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
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
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;
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
    void retriesOneTransientServerFailureAndThenReturnsTheResponse() {
        UUID productId = UUID.randomUUID();
        RestClient.Builder builder = RestClient.builder();
        TestClient testClient = client(builder);
        MockRestServiceServer server = testClient.server();
        RestProductCatalogClient client = testClient.client();
        server.expect(requestTo("http://product.test/api/v1/products/batch?ids=" + productId))
                .andRespond(withServerError().body("private downstream failure"));
        server.expect(requestTo("http://product.test/api/v1/products/batch?ids=" + productId))
                .andRespond(withSuccess(productResponse(productId), MediaType.APPLICATION_JSON));

        List<CatalogProductResponse> result = client.getProducts(Set.of(productId));

        assertThat(result).singleElement()
                .extracting(CatalogProductResponse::id)
                .isEqualTo(productId);
        assertThat(testClient.retry().getMetrics().getNumberOfSuccessfulCallsWithRetryAttempt()).isEqualTo(1);
        assertThat(testClient.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isZero();
        server.verify();
    }

    @Test
    void doesNotRetryClientErrorsOrRecordThemAsDependencyFailures() {
        UUID productId = UUID.randomUUID();
        TestClient testClient = client(RestClient.builder());
        testClient.server()
                .expect(once(), requestTo("http://product.test/api/v1/products/batch?ids=" + productId))
                .andRespond(withBadRequest().body("invalid downstream request"));

        assertThatThrownBy(() -> testClient.client().getProducts(Set.of(productId)))
                .isInstanceOf(ProductCatalogUnavailableException.class)
                .hasMessage("Product catalog is temporarily unavailable");

        assertThat(testClient.retry().getMetrics().getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(1);
        assertThat(testClient.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isZero();
        testClient.server().verify();
    }

    @Test
    void recordsAnEmptySuccessfulResponseAsAContractFailureWithoutRetrying() {
        UUID productId = UUID.randomUUID();
        TestClient testClient = client(RestClient.builder());
        testClient.server()
                .expect(once(), requestTo("http://product.test/api/v1/products/batch?ids=" + productId))
                .andRespond(withNoContent());

        assertThatThrownBy(() -> testClient.client().getProducts(Set.of(productId)))
                .isInstanceOf(ProductCatalogUnavailableException.class);

        assertThat(testClient.retry().getMetrics().getNumberOfFailedCallsWithoutRetryAttempt()).isEqualTo(1);
        assertThat(testClient.circuitBreaker().getMetrics().getNumberOfFailedCalls()).isEqualTo(1);
        testClient.server().verify();
    }

    @Test
    void opensAfterRepeatedFailuresFailsFastAndClosesAfterAProbeSucceeds() {
        UUID productId = UUID.randomUUID();
        ProductCatalogProperties.Resilience policy = new ProductCatalogProperties.Resilience(
                1,
                Duration.ofMillis(1),
                50,
                2,
                2,
                1,
                Duration.ofSeconds(10)
        );
        TestClient testClient = client(RestClient.builder(), policy);
        String requestUrl = "http://product.test/api/v1/products/batch?ids=" + productId;
        testClient.server().expect(requestTo(requestUrl)).andRespond(withServerError());
        testClient.server().expect(requestTo(requestUrl)).andRespond(withServerError());
        testClient.server().expect(requestTo(requestUrl))
                .andRespond(withSuccess(productResponse(productId), MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> testClient.client().getProducts(Set.of(productId)))
                .isInstanceOf(ProductCatalogUnavailableException.class);
        assertThatThrownBy(() -> testClient.client().getProducts(Set.of(productId)))
                .isInstanceOf(ProductCatalogUnavailableException.class);
        assertThat(testClient.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.OPEN);

        assertThatThrownBy(() -> testClient.client().getProducts(Set.of(productId)))
                .isInstanceOf(ProductCatalogUnavailableException.class);
        assertThat(testClient.circuitBreaker().getMetrics().getNumberOfNotPermittedCalls()).isEqualTo(1);

        testClient.circuitBreaker().transitionToHalfOpenState();
        assertThat(testClient.client().getProducts(Set.of(productId))).hasSize(1);
        assertThat(testClient.circuitBreaker().getState()).isEqualTo(CircuitBreaker.State.CLOSED);
        testClient.server().verify();
    }

    private TestClient client(RestClient.Builder builder) {
        return client(builder, new ProductCatalogProperties.Resilience(
                2,
                Duration.ofMillis(1),
                50,
                4,
                2,
                1,
                Duration.ofSeconds(10)
        ));
    }

    private TestClient client(
            RestClient.Builder builder,
            ProductCatalogProperties.Resilience policy
    ) {
        ProductCatalogProperties properties = new ProductCatalogProperties(
                "http://product.test",
                Duration.ofSeconds(2),
                Duration.ofSeconds(3),
                policy
        );
        ProductCatalogClientConfiguration configuration = new ProductCatalogClientConfiguration();
        RestClient.Builder configuredBuilder = configuration.configureProductCatalogClient(builder, properties);
        MockRestServiceServer server = MockRestServiceServer.bindTo(configuredBuilder).build();
        RestClient restClient = configuredBuilder.build();
        Retry retry = configuration.productCatalogRetry(RetryRegistry.ofDefaults(), properties);
        CircuitBreaker circuitBreaker = configuration.productCatalogCircuitBreaker(
                CircuitBreakerRegistry.ofDefaults(),
                properties
        );
        return new TestClient(
                new RestProductCatalogClient(restClient, retry, circuitBreaker),
                server,
                retry,
                circuitBreaker
        );
    }

    private String productResponse(UUID productId) {
        return """
                [{
                  "id":"%s",
                  "sku":"SKU-001",
                  "name":"Product One",
                  "price":10.00,
                  "currency":"USD",
                  "status":"ACTIVE"
                }]
                """.formatted(productId);
    }

    private record TestClient(
            RestProductCatalogClient client,
            MockRestServiceServer server,
            Retry retry,
            CircuitBreaker circuitBreaker
    ) {
    }
}
