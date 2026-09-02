package com.example.ecommerce.product.cache;

import com.example.ecommerce.product.dto.ProductResponse;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface ProductCache {

    Optional<ProductResponse> get(UUID productId);

    Map<UUID, ProductResponse> getAll(Set<UUID> productIds);

    void put(ProductResponse product);

    void putAll(Collection<ProductResponse> products);

    void evict(UUID productId);
}
