package com.example.ecommerce.order.client;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public interface ProductCatalogClient {

    List<CatalogProductResponse> getProducts(Set<UUID> productIds);
}
