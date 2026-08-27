package com.example.ecommerce.product.service;

import com.example.ecommerce.product.domain.Product;
import com.example.ecommerce.product.domain.ProductStatus;
import com.example.ecommerce.product.dto.CreateProductRequest;
import com.example.ecommerce.product.dto.UpdateProductRequest;
import com.example.ecommerce.product.exception.InvalidPriceRangeException;
import com.example.ecommerce.product.exception.InvalidSortFieldException;
import com.example.ecommerce.product.exception.ProductSkuConflictException;
import com.example.ecommerce.product.exception.ProductVersionConflictException;
import com.example.ecommerce.product.mapper.ProductMapper;
import com.example.ecommerce.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, new ProductMapper());
    }

    @Test
    void createsNormalizedProduct() {
        when(productRepository.existsBySku("LAPTOP-001")).thenReturn(false);
        when(productRepository.saveAndFlush(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = productService.createProduct(new CreateProductRequest(
                " laptop-001 ",
                "Developer Laptop",
                null,
                new BigDecimal("1499.00"),
                "USD",
                ProductStatus.ACTIVE
        ));

        assertThat(response.sku()).isEqualTo("LAPTOP-001");
        verify(productRepository).saveAndFlush(any(Product.class));
    }

    @Test
    void rejectsDuplicateSkuBeforeInsert() {
        when(productRepository.existsBySku("LAPTOP-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(new CreateProductRequest(
                "LAPTOP-001",
                "Developer Laptop",
                null,
                new BigDecimal("1499.00"),
                "USD",
                ProductStatus.ACTIVE
        ))).isInstanceOf(ProductSkuConflictException.class);

        verify(productRepository, never()).saveAndFlush(any(Product.class));
    }

    @Test
    void rejectsStaleVersionOnUpdate() {
        UUID productId = UUID.randomUUID();
        Product product = product("LAPTOP-001");
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.updateProduct(productId, new UpdateProductRequest(
                "Updated Laptop",
                null,
                new BigDecimal("1599.00"),
                "USD",
                ProductStatus.ACTIVE,
                1L
        ))).isInstanceOf(ProductVersionConflictException.class);

        verify(productRepository, never()).saveAndFlush(any(Product.class));
    }

    @Test
    void rejectsInvertedPriceRange() {
        assertThatThrownBy(() -> productService.searchProducts(
                null,
                null,
                new BigDecimal("100.00"),
                new BigDecimal("50.00"),
                0,
                20,
                "createdAt",
                Sort.Direction.DESC
        )).isInstanceOf(InvalidPriceRangeException.class);
    }

    @Test
    void rejectsUnsupportedSortField() {
        assertThatThrownBy(() -> productService.searchProducts(
                null,
                null,
                null,
                null,
                0,
                20,
                "description",
                Sort.Direction.ASC
        )).isInstanceOf(InvalidSortFieldException.class);
    }

    private Product product(String sku) {
        return Product.create(
                sku,
                "Developer Laptop",
                null,
                new BigDecimal("1499.00"),
                "USD",
                ProductStatus.ACTIVE
        );
    }
}
