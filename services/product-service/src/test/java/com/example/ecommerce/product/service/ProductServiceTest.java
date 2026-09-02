package com.example.ecommerce.product.service;

import com.example.ecommerce.product.cache.ProductCache;
import com.example.ecommerce.product.domain.Product;
import com.example.ecommerce.product.domain.ProductStatus;
import com.example.ecommerce.product.dto.CreateProductRequest;
import com.example.ecommerce.product.dto.ProductResponse;
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
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
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

    @Mock
    private ProductCache productCache;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository, new ProductMapper(), productCache);
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
    void returnsSingleProductFromCacheWithoutQueryingPostgres() {
        UUID productId = UUID.randomUUID();
        ProductResponse cached = response(productId, "LAPTOP-001", "Cached Laptop");
        when(productCache.get(productId)).thenReturn(Optional.of(cached));

        ProductResponse result = productService.getProduct(productId);

        assertThat(result).isSameAs(cached);
        verify(productRepository, never()).findById(any());
    }

    @Test
    void loadsAndCachesSingleProductOnCacheMiss() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId, "LAPTOP-001");
        when(productCache.get(productId)).thenReturn(Optional.empty());
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));

        ProductResponse result = productService.getProduct(productId);

        assertThat(result.id()).isEqualTo(productId);
        verify(productCache).put(result);
    }

    @Test
    void batchReadUsesCacheAndOneRepositoryQueryForAllMisses() {
        UUID cachedId = UUID.randomUUID();
        UUID missingId = UUID.randomUUID();
        ProductResponse cached = response(cachedId, "LAPTOP-001", "Cached Laptop");
        Product loaded = product(missingId, "PHONE-001");
        Set<UUID> requested = Set.of(cachedId, missingId);
        when(productCache.getAll(requested)).thenReturn(Map.of(cachedId, cached));
        when(productRepository.findAllById(Set.of(missingId))).thenReturn(List.of(loaded));

        List<ProductResponse> result = productService.getProducts(requested);

        assertThat(result).extracting(ProductResponse::id)
                .containsExactlyInAnyOrder(cachedId, missingId);
        verify(productRepository).findAllById(Set.of(missingId));
        verify(productCache).putAll(any());
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
    void evictsCachedProductAfterSuccessfulUpdate() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId, "LAPTOP-001");
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.saveAndFlush(product)).thenReturn(product);

        productService.updateProduct(productId, new UpdateProductRequest(
                "Updated Laptop",
                null,
                new BigDecimal("1599.00"),
                "USD",
                ProductStatus.ACTIVE,
                0L
        ));

        verify(productCache).evict(productId);
    }

    @Test
    void waitsForTransactionCommitBeforeEvictingCachedProduct() {
        UUID productId = UUID.randomUUID();
        Product product = product(productId, "LAPTOP-001");
        when(productRepository.findById(productId)).thenReturn(Optional.of(product));
        when(productRepository.saveAndFlush(product)).thenReturn(product);

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            productService.updateProduct(productId, new UpdateProductRequest(
                    "Updated Laptop",
                    null,
                    new BigDecimal("1599.00"),
                    "USD",
                    ProductStatus.ACTIVE,
                    0L
            ));

            verify(productCache, never()).evict(productId);
            TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);
            verify(productCache).evict(productId);
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
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

    private Product product(UUID productId, String sku) {
        Product product = product(sku);
        ReflectionTestUtils.setField(product, "id", productId);
        ReflectionTestUtils.setField(product, "createdAt", Instant.parse("2026-01-01T00:00:00Z"));
        ReflectionTestUtils.setField(product, "updatedAt", Instant.parse("2026-01-01T00:00:00Z"));
        return product;
    }

    private ProductResponse response(UUID productId, String sku, String name) {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        return new ProductResponse(
                productId,
                sku,
                name,
                null,
                new BigDecimal("1499.00"),
                "USD",
                ProductStatus.ACTIVE,
                0L,
                timestamp,
                timestamp
        );
    }
}
