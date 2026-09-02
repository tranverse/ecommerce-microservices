package com.example.ecommerce.product.cache;

import com.example.ecommerce.product.domain.ProductStatus;
import com.example.ecommerce.product.dto.ProductResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RedisProductCacheTest {

    private static final Duration TTL = Duration.ofMinutes(5);

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private SimpleMeterRegistry meterRegistry;
    private RedisProductCache productCache;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules();
        productCache = new RedisProductCache(
                redisTemplate,
                objectMapper,
                new ProductCacheProperties(true, "test:product:", TTL),
                meterRegistry
        );
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void treatsRedisReadFailureAsCacheMiss() {
        UUID productId = UUID.randomUUID();
        when(valueOperations.get("test:product:" + productId))
                .thenThrow(new RedisConnectionFailureException("offline"));

        Optional<ProductResponse> result = productCache.get(productId);

        assertThat(result).isEmpty();
        assertThat(meterRegistry.get("ecommerce.product.cache.errors")
                .tag("operation", "read").counter().count()).isEqualTo(1.0);
    }

    @Test
    void removesCorruptValueAndFallsBackToSourceOfTruth() {
        UUID productId = UUID.randomUUID();
        when(valueOperations.get("test:product:" + productId)).thenReturn("{not-json");

        Optional<ProductResponse> result = productCache.get(productId);

        assertThat(result).isEmpty();
        verify(redisTemplate).delete("test:product:" + productId);
        assertThat(meterRegistry.get("ecommerce.product.cache.errors")
                .tag("operation", "deserialize").counter().count()).isEqualTo(1.0);
    }

    @Test
    void doesNotFailBusinessWriteWhenRedisWriteFails() {
        ProductResponse product = product(UUID.randomUUID());
        doThrow(new RedisConnectionFailureException("offline"))
                .when(valueOperations)
                .set(anyString(), anyString(), eq(TTL));

        assertThatCode(() -> productCache.put(product)).doesNotThrowAnyException();

        assertThat(meterRegistry.get("ecommerce.product.cache.errors")
                .tag("operation", "write").counter().count()).isEqualTo(1.0);
    }

    private ProductResponse product(UUID productId) {
        Instant timestamp = Instant.parse("2026-01-01T00:00:00Z");
        return new ProductResponse(
                productId,
                "LAPTOP-001",
                "Developer Laptop",
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
