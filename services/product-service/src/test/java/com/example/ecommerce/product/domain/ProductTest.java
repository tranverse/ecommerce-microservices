package com.example.ecommerce.product.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class ProductTest {

    @Test
    void createsProductWithNormalizedValues() {
        Product product = Product.create(
                " laptop-001 ",
                "  Developer Laptop ",
                "  A fast laptop  ",
                new BigDecimal("1499.00"),
                " usd ",
                ProductStatus.DRAFT
        );

        assertThat(product.getSku()).isEqualTo("LAPTOP-001");
        assertThat(product.getName()).isEqualTo("Developer Laptop");
        assertThat(product.getDescription()).isEqualTo("A fast laptop");
        assertThat(product.getCurrency()).isEqualTo("USD");
        assertThat(product.getStatus()).isEqualTo(ProductStatus.DRAFT);
    }

    @Test
    void rejectsNonPositivePrice() {
        assertThatIllegalArgumentException().isThrownBy(() -> Product.create(
                "LAPTOP-001",
                "Developer Laptop",
                null,
                BigDecimal.ZERO,
                "USD",
                ProductStatus.DRAFT
        )).withMessage("price must be greater than zero");
    }

    @Test
    void convertsBlankDescriptionToNull() {
        Product product = Product.create(
                "LAPTOP-001",
                "Developer Laptop",
                "   ",
                new BigDecimal("1499.00"),
                "USD",
                ProductStatus.DRAFT
        );

        assertThat(product.getDescription()).isNull();
    }

    @Test
    void rejectsUnsupportedCurrency() {
        assertThatIllegalArgumentException().isThrownBy(() -> Product.create(
                "LAPTOP-001",
                "Developer Laptop",
                null,
                new BigDecimal("1499.00"),
                "ZZZ",
                ProductStatus.DRAFT
        ));
    }
}
