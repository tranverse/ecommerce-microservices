package com.example.ecommerce.product.cache;

import com.example.ecommerce.product.dto.ProductResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class RedisProductCache implements ProductCache {

    private static final Logger log = LoggerFactory.getLogger(RedisProductCache.class);
    private static final long FAILURE_LOG_INTERVAL_NANOS = TimeUnit.SECONDS.toNanos(30);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final ProductCacheProperties properties;
    private final MeterRegistry meterRegistry;
    private final AtomicLong nextFailureLogAt = new AtomicLong();

    public RedisProductCache(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            ProductCacheProperties properties,
            MeterRegistry meterRegistry
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.meterRegistry = meterRegistry;
    }

    @Override
    public Optional<ProductResponse> get(UUID productId) {
        Objects.requireNonNull(productId, "productId must not be null");
        if (!properties.enabled()) {
            return Optional.empty();
        }

        try {
            String value = redisTemplate.opsForValue().get(key(productId));
            if (value == null) {
                recordRequest("miss");
                return Optional.empty();
            }
            Optional<ProductResponse> product = deserialize(productId, value);
            recordRequest(product.isPresent() ? "hit" : "miss");
            return product;
        } catch (RuntimeException exception) {
            recordError("read", exception);
            recordRequest("miss");
            return Optional.empty();
        }
    }

    @Override
    public Map<UUID, ProductResponse> getAll(Set<UUID> productIds) {
        Objects.requireNonNull(productIds, "productIds must not be null");
        if (!properties.enabled() || productIds.isEmpty()) {
            return Map.of();
        }

        List<UUID> orderedIds = productIds.stream().sorted().toList();
        List<String> keys = orderedIds.stream().map(this::key).toList();
        try {
            List<String> values = redisTemplate.opsForValue().multiGet(keys);
            if (values == null) {
                recordRequests("miss", orderedIds.size());
                return Map.of();
            }

            Map<UUID, ProductResponse> cached = new LinkedHashMap<>();
            for (int index = 0; index < orderedIds.size(); index++) {
                UUID productId = orderedIds.get(index);
                String value = values.get(index);
                if (value == null) {
                    recordRequest("miss");
                    continue;
                }
                Optional<ProductResponse> product = deserialize(productId, value);
                recordRequest(product.isPresent() ? "hit" : "miss");
                product.ifPresent(response -> cached.put(productId, response));
            }
            return Map.copyOf(cached);
        } catch (RuntimeException exception) {
            recordError("read-many", exception);
            recordRequests("miss", orderedIds.size());
            return Map.of();
        }
    }

    @Override
    public void put(ProductResponse product) {
        Objects.requireNonNull(product, "product must not be null");
        if (!properties.enabled()) {
            return;
        }

        try {
            redisTemplate.opsForValue().set(
                    key(product.id()),
                    objectMapper.writeValueAsString(product),
                    properties.ttl()
            );
            meterRegistry.counter("ecommerce.product.cache.writes").increment();
        } catch (JsonProcessingException | RuntimeException exception) {
            recordError("write", exception);
        }
    }

    @Override
    public void putAll(Collection<ProductResponse> products) {
        Objects.requireNonNull(products, "products must not be null");
        if (!properties.enabled() || products.isEmpty()) {
            return;
        }

        try {
            List<SerializedProduct> serialized = new ArrayList<>(products.size());
            products.stream()
                    .sorted(Comparator.comparing(ProductResponse::id))
                    .forEach(product -> serialized.add(serialize(product)));

            RedisSerializer<String> serializer = redisTemplate.getStringSerializer();
            redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
                for (SerializedProduct product : serialized) {
                    connection.stringCommands().set(
                            serializer.serialize(product.key()),
                            serializer.serialize(product.value()),
                            Expiration.from(properties.ttl()),
                            RedisStringCommands.SetOption.UPSERT
                    );
                }
                return null;
            });
            meterRegistry.counter("ecommerce.product.cache.writes").increment(serialized.size());
        } catch (RuntimeException exception) {
            recordError("write-many", exception);
        }
    }

    @Override
    public void evict(UUID productId) {
        Objects.requireNonNull(productId, "productId must not be null");
        if (!properties.enabled()) {
            return;
        }

        try {
            redisTemplate.delete(key(productId));
            meterRegistry.counter("ecommerce.product.cache.evictions").increment();
        } catch (RuntimeException exception) {
            recordError("evict", exception);
        }
    }

    private Optional<ProductResponse> deserialize(UUID productId, String value) {
        try {
            ProductResponse product = objectMapper.readValue(value, ProductResponse.class);
            if (!productId.equals(product.id())) {
                recordError("validate", new IllegalStateException("Cached product ID does not match its key"));
                bestEffortDelete(productId);
                return Optional.empty();
            }
            return Optional.of(product);
        } catch (JsonProcessingException exception) {
            recordError("deserialize", exception);
            bestEffortDelete(productId);
            return Optional.empty();
        }
    }

    private SerializedProduct serialize(ProductResponse product) {
        try {
            return new SerializedProduct(key(product.id()), objectMapper.writeValueAsString(product));
        } catch (JsonProcessingException exception) {
            throw new CacheSerializationException(exception);
        }
    }

    private void bestEffortDelete(UUID productId) {
        try {
            redisTemplate.delete(key(productId));
        } catch (RuntimeException exception) {
            recordError("delete-invalid", exception);
        }
    }

    private String key(UUID productId) {
        return properties.keyPrefix() + productId;
    }

    private void recordRequest(String result) {
        meterRegistry.counter("ecommerce.product.cache.requests", "result", result).increment();
    }

    private void recordRequests(String result, int count) {
        meterRegistry.counter("ecommerce.product.cache.requests", "result", result).increment(count);
    }

    private void recordError(String operation, Exception exception) {
        meterRegistry.counter("ecommerce.product.cache.errors", "operation", operation).increment();
        long now = System.nanoTime();
        long next = nextFailureLogAt.get();
        if (now >= next && nextFailureLogAt.compareAndSet(next, now + FAILURE_LOG_INTERVAL_NANOS)) {
            log.warn("Product cache operation failed operation={} exception={}",
                    operation, exception.getClass().getSimpleName());
        }
    }

    private record SerializedProduct(String key, String value) {
    }

    private static final class CacheSerializationException extends RuntimeException {

        private CacheSerializationException(JsonProcessingException cause) {
            super(cause);
        }
    }
}
