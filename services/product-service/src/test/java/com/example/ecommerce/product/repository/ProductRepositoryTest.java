package com.example.ecommerce.product.repository;

import com.example.ecommerce.product.TestcontainersConfiguration;
import com.example.ecommerce.product.config.JpaAuditingConfiguration;
import com.example.ecommerce.product.domain.Product;
import com.example.ecommerce.product.domain.ProductStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({TestcontainersConfiguration.class, JpaAuditingConfiguration.class})
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void persistsAuditedProductAndFindsItBySku() {
        Product product = productRepository.saveAndFlush(product(
                "LAPTOP-001",
                "Developer Laptop",
                "1499.00",
                ProductStatus.ACTIVE
        ));

        Product persisted = productRepository.findBySku("LAPTOP-001").orElseThrow();

        assertThat(persisted.getId()).isEqualTo(product.getId());
        assertThat(persisted.getCreatedAt()).isNotNull();
        assertThat(persisted.getUpdatedAt()).isNotNull();
        assertThat(persisted.getVersion()).isZero();
    }

    @Test
    void enforcesUniqueSkuAtTheDatabaseBoundary() {
        productRepository.saveAndFlush(product("LAPTOP-001", "Laptop One", "999.00", ProductStatus.ACTIVE));

        assertThatThrownBy(() ->
                productRepository.saveAndFlush(product("LAPTOP-001", "Laptop Two", "1299.00", ProductStatus.DRAFT))
        ).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void filtersBySearchStatusAndPriceRange() {
        productRepository.save(product("LAPTOP-001", "Developer Laptop", "1499.00", ProductStatus.ACTIVE));
        productRepository.save(product("PHONE-001", "Developer Phone", "799.00", ProductStatus.ACTIVE));
        productRepository.save(product("LAPTOP-OLD", "Legacy Laptop", "499.00", ProductStatus.INACTIVE));
        productRepository.flush();

        Page<Product> result = productRepository.findAll(
                ProductSpecifications.matching(
                        "laptop",
                        ProductStatus.ACTIVE,
                        new BigDecimal("1000.00"),
                        new BigDecimal("2000.00")
                ),
                PageRequest.of(0, 20)
        );

        assertThat(result.getContent())
                .extracting(Product::getSku)
                .containsExactly("LAPTOP-001");
    }

    private Product product(String sku, String name, String price, ProductStatus status) {
        return Product.create(sku, name, null, new BigDecimal(price), status);
    }
}
