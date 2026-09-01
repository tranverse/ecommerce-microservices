package com.example.ecommerce.order.config;

import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class ProductCatalogPropertiesTest {

    @Test
    void rejectsUnsafeRetryAndCircuitBreakerConfiguration() {
        ProductCatalogProperties properties = new ProductCatalogProperties(
                "http://product.test",
                Duration.ofSeconds(1),
                Duration.ofSeconds(1),
                new ProductCatalogProperties.Resilience(
                        4,
                        Duration.ZERO,
                        50,
                        5,
                        6,
                        1,
                        Duration.ofSeconds(1)
                )
        );

        try (var validatorFactory = Validation.buildDefaultValidatorFactory()) {
            assertThat(validatorFactory.getValidator().validate(properties))
                    .extracting(violation -> violation.getPropertyPath().toString())
                    .contains(
                            "resilience.maxAttempts",
                            "resilience.retryWaitDuration",
                            "resilience.windowConfigurationValid"
                    );
        }
    }
}
