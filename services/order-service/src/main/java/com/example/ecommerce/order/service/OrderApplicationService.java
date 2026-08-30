package com.example.ecommerce.order.service;

import com.example.ecommerce.order.client.CatalogProductResponse;
import com.example.ecommerce.order.client.ProductCatalogClient;
import com.example.ecommerce.order.domain.ProductSnapshot;
import com.example.ecommerce.order.dto.CreateOrderItemRequest;
import com.example.ecommerce.order.dto.CreateOrderRequest;
import com.example.ecommerce.order.dto.OrderResponse;
import com.example.ecommerce.order.dto.OrderSummaryResponse;
import com.example.ecommerce.order.dto.PageResponse;
import com.example.ecommerce.order.exception.InvalidOrderItemException;
import com.example.ecommerce.order.exception.ProductCatalogUnavailableException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Currency;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

@Service
public class OrderApplicationService {

    private final ProductCatalogClient productCatalogClient;
    private final OrderPersistenceService persistenceService;

    public OrderApplicationService(
            ProductCatalogClient productCatalogClient,
            OrderPersistenceService persistenceService
    ) {
        this.productCatalogClient = productCatalogClient;
        this.persistenceService = persistenceService;
    }

    public OrderResponse createOrder(
            UUID customerId,
            String idempotencyKey,
            CreateOrderRequest request
    ) {
        Map<UUID, Integer> quantities = normalizeItems(request.items());
        String requestHash = requestHash(quantities);

        var existing = persistenceService.findIdempotentOrder(customerId, idempotencyKey, requestHash);
        if (existing.isPresent()) {
            return existing.get();
        }

        List<CatalogProductResponse> products = productCatalogClient.getProducts(quantities.keySet());
        List<ProductSnapshot> snapshots = trustedSnapshots(quantities, products);

        try {
            return persistenceService.createOrder(customerId, idempotencyKey, requestHash, snapshots);
        } catch (DataIntegrityViolationException exception) {
            return persistenceService.findIdempotentOrder(customerId, idempotencyKey, requestHash)
                    .orElseThrow(() -> exception);
        }
    }

    public OrderResponse getOrder(UUID customerId, UUID orderId) {
        return persistenceService.getOrder(customerId, orderId);
    }

    public PageResponse<OrderSummaryResponse> listOrders(UUID customerId, int page, int size) {
        return persistenceService.listOrders(customerId, page, size);
    }

    private Map<UUID, Integer> normalizeItems(List<CreateOrderItemRequest> items) {
        Map<UUID, Integer> quantities = new TreeMap<>();
        for (CreateOrderItemRequest item : items) {
            if (quantities.putIfAbsent(item.productId(), item.quantity()) != null) {
                throw new InvalidOrderItemException("Order must not contain duplicate products");
            }
        }
        return Map.copyOf(quantities);
    }

    private List<ProductSnapshot> trustedSnapshots(
            Map<UUID, Integer> quantities,
            List<CatalogProductResponse> products
    ) {
        Map<UUID, CatalogProductResponse> productsById = new HashMap<>();
        for (CatalogProductResponse product : products) {
            validateCatalogContract(product);
            if (productsById.put(product.id(), product) != null) {
                throw new ProductCatalogUnavailableException();
            }
        }

        Set<UUID> missingIds = new HashSet<>(quantities.keySet());
        missingIds.removeAll(productsById.keySet());
        if (!missingIds.isEmpty()) {
            throw new InvalidOrderItemException("Some products were not found: " + sortedIds(missingIds));
        }

        List<UUID> inactiveIds = products.stream()
                .filter(product -> !"ACTIVE".equals(product.status()))
                .map(CatalogProductResponse::id)
                .sorted()
                .toList();
        if (!inactiveIds.isEmpty()) {
            throw new InvalidOrderItemException("Some products are not active: " + inactiveIds);
        }

        Set<String> currencies = products.stream()
                .map(product -> product.currency().toUpperCase(Locale.ROOT))
                .collect(java.util.stream.Collectors.toSet());
        if (currencies.size() != 1) {
            throw new InvalidOrderItemException("All products in an order must use the same currency");
        }

        List<ProductSnapshot> snapshots = new ArrayList<>();
        quantities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> {
                    CatalogProductResponse product = productsById.get(entry.getKey());
                    snapshots.add(new ProductSnapshot(
                            product.id(),
                            product.sku(),
                            product.name(),
                            product.price(),
                            product.currency(),
                            entry.getValue()
                    ));
                });
        return List.copyOf(snapshots);
    }

    private void validateCatalogContract(CatalogProductResponse product) {
        if (product == null
                || product.id() == null
                || product.sku() == null
                || product.sku().isBlank()
                || product.name() == null
                || product.name().isBlank()
                || product.price() == null
                || product.price().signum() <= 0
                || product.price().stripTrailingZeros().scale() > 2
                || product.currency() == null
                || product.status() == null) {
            throw new ProductCatalogUnavailableException();
        }
        try {
            Currency.getInstance(product.currency().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new ProductCatalogUnavailableException();
        }
    }

    private String requestHash(Map<UUID, Integer> quantities) {
        StringBuilder canonical = new StringBuilder();
        quantities.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> canonical
                        .append(entry.getKey())
                        .append(':')
                        .append(entry.getValue())
                        .append('\n'));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private List<UUID> sortedIds(Set<UUID> ids) {
        return ids.stream().sorted(Comparator.naturalOrder()).toList();
    }
}
