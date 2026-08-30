package com.example.ecommerce.product;

import com.example.ecommerce.product.domain.ProductStatus;
import com.example.ecommerce.product.dto.CreateProductRequest;
import com.example.ecommerce.product.dto.ProductResponse;
import com.example.ecommerce.product.dto.UpdateProductRequest;
import com.example.ecommerce.product.exception.ApiErrorResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ProductApiIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void createsReadsUpdatesAndRejectsStaleVersion() {
        String sku = ("LAPTOP-" + UUID.randomUUID().toString().substring(0, 8)).toUpperCase(Locale.ROOT);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-Correlation-ID", "integration-product-flow");

        ResponseEntity<ProductResponse> createResponse = restTemplate.exchange(
                "/api/v1/products",
                HttpMethod.POST,
                new HttpEntity<>(new CreateProductRequest(
                        sku,
                        "Developer Laptop",
                        "Created by an integration test",
                        new BigDecimal("1499.00"),
                        "USD",
                        ProductStatus.DRAFT
                ), headers),
                ProductResponse.class
        );

        assertThat(createResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(createResponse.getHeaders().getFirst("X-Correlation-ID"))
                .isEqualTo("integration-product-flow");
        ProductResponse created = createResponse.getBody();
        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.createdAt()).isNotNull();

        ResponseEntity<ProductResponse> getResponse = restTemplate.getForEntity(
                "/api/v1/products/{id}",
                ProductResponse.class,
                created.id()
        );
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody()).extracting(ProductResponse::sku).isEqualTo(sku);

        UpdateProductRequest update = new UpdateProductRequest(
                "Developer Laptop Pro",
                "Updated once",
                new BigDecimal("1699.00"),
                "USD",
                ProductStatus.ACTIVE,
                created.version()
        );
        ResponseEntity<ProductResponse> updateResponse = restTemplate.exchange(
                "/api/v1/products/{id}",
                HttpMethod.PUT,
                new HttpEntity<>(update, headers),
                ProductResponse.class,
                created.id()
        );
        assertThat(updateResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(updateResponse.getBody()).extracting(ProductResponse::version).isEqualTo(1L);

        ResponseEntity<ProductResponse[]> batchResponse = restTemplate.getForEntity(
                "/api/v1/products/batch?ids={firstId}&ids={missingId}",
                ProductResponse[].class,
                created.id(),
                UUID.randomUUID()
        );
        assertThat(batchResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(List.of(batchResponse.getBody()))
                .extracting(ProductResponse::id)
                .containsExactly(created.id());

        ResponseEntity<ApiErrorResponse> staleResponse = restTemplate.exchange(
                "/api/v1/products/{id}",
                HttpMethod.PUT,
                new HttpEntity<>(update, headers),
                ApiErrorResponse.class,
                created.id()
        );
        assertThat(staleResponse.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(staleResponse.getBody())
                .extracting(ApiErrorResponse::errorCode)
                .isEqualTo("PRODUCT_VERSION_CONFLICT");
    }
}
