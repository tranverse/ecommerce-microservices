package com.example.ecommerce.product.repository;

import com.example.ecommerce.product.domain.Product;
import com.example.ecommerce.product.domain.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ProductSpecifications {

    private ProductSpecifications() {
    }

    public static Specification<Product> matching(
            String searchTerm,
            ProductStatus status,
            BigDecimal minimumPrice,
            BigDecimal maximumPrice
    ) {
        List<Specification<Product>> specifications = new ArrayList<>();

        if (searchTerm != null && !searchTerm.isBlank()) {
            String pattern = "%" + searchTerm.trim().toLowerCase(Locale.ROOT) + "%";
            specifications.add((root, query, criteriaBuilder) -> criteriaBuilder.or(
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("name")), pattern),
                    criteriaBuilder.like(criteriaBuilder.lower(root.get("sku")), pattern)
            ));
        }
        if (status != null) {
            specifications.add((root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get("status"), status));
        }
        if (minimumPrice != null) {
            specifications.add((root, query, criteriaBuilder) ->
                    criteriaBuilder.greaterThanOrEqualTo(root.get("price"), minimumPrice));
        }
        if (maximumPrice != null) {
            specifications.add((root, query, criteriaBuilder) ->
                    criteriaBuilder.lessThanOrEqualTo(root.get("price"), maximumPrice));
        }

        return Specification.allOf(specifications);
    }
}
