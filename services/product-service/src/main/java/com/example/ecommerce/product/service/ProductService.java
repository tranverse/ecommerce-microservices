package com.example.ecommerce.product.service;

import com.example.ecommerce.product.domain.Product;
import com.example.ecommerce.product.domain.ProductStatus;
import com.example.ecommerce.product.dto.CreateProductRequest;
import com.example.ecommerce.product.dto.PageResponse;
import com.example.ecommerce.product.dto.ProductResponse;
import com.example.ecommerce.product.dto.UpdateProductRequest;
import com.example.ecommerce.product.exception.InvalidPriceRangeException;
import com.example.ecommerce.product.exception.InvalidSortFieldException;
import com.example.ecommerce.product.exception.ProductNotFoundException;
import com.example.ecommerce.product.exception.ProductSkuConflictException;
import com.example.ecommerce.product.exception.ProductVersionConflictException;
import com.example.ecommerce.product.mapper.ProductMapper;
import com.example.ecommerce.product.repository.ProductRepository;
import com.example.ecommerce.product.repository.ProductSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);
    private static final Set<String> ALLOWED_SORT_FIELDS = Set.of("name", "sku", "price", "status", "createdAt");

    private final ProductRepository productRepository;
    private final ProductMapper productMapper;

    public ProductService(ProductRepository productRepository, ProductMapper productMapper) {
        this.productRepository = productRepository;
        this.productMapper = productMapper;
    }

    @Transactional
    public ProductResponse createProduct(CreateProductRequest request) {
        Product product = Product.create(
                request.sku(),
                request.name(),
                request.description(),
                request.price(),
                request.currency(),
                request.status()
        );

        if (productRepository.existsBySku(product.getSku())) {
            throw new ProductSkuConflictException(product.getSku());
        }

        try {
            Product saved = productRepository.saveAndFlush(product);
            log.info("Created product id={} sku={}", saved.getId(), saved.getSku());
            return productMapper.toResponse(saved);
        } catch (DataIntegrityViolationException exception) {
            throw new ProductSkuConflictException(product.getSku());
        }
    }

    @Transactional(readOnly = true)
    public ProductResponse getProduct(UUID productId) {
        return productMapper.toResponse(findProduct(productId));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> searchProducts(
            String searchTerm,
            ProductStatus status,
            BigDecimal minimumPrice,
            BigDecimal maximumPrice,
            int page,
            int size,
            String sortBy,
            Sort.Direction direction
    ) {
        validatePriceRange(minimumPrice, maximumPrice);
        String safeSortField = resolveSortField(sortBy);
        PageRequest pageRequest = PageRequest.of(page, size, Sort.by(direction, safeSortField));
        Page<Product> products = productRepository.findAll(
                ProductSpecifications.matching(searchTerm, status, minimumPrice, maximumPrice),
                pageRequest
        );
        return PageResponse.from(products, productMapper::toResponse);
    }

    @Transactional
    public ProductResponse updateProduct(UUID productId, UpdateProductRequest request) {
        Product product = findProduct(productId);
        if (product.getVersion() != request.version()) {
            throw new ProductVersionConflictException(productId);
        }

        product.updateDetails(
                request.name(),
                request.description(),
                request.price(),
                request.currency(),
                request.status()
        );
        Product saved = productRepository.saveAndFlush(product);
        log.info("Updated product id={} version={}", saved.getId(), saved.getVersion());
        return productMapper.toResponse(saved);
    }

    private Product findProduct(UUID productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new ProductNotFoundException(productId));
    }

    private void validatePriceRange(BigDecimal minimumPrice, BigDecimal maximumPrice) {
        if (minimumPrice != null && maximumPrice != null && minimumPrice.compareTo(maximumPrice) > 0) {
            throw new InvalidPriceRangeException();
        }
    }

    private String resolveSortField(String sortBy) {
        String candidate = sortBy == null ? "createdAt" : sortBy.trim();
        if (!ALLOWED_SORT_FIELDS.contains(candidate)) {
            throw new InvalidSortFieldException(candidate, ALLOWED_SORT_FIELDS.stream().sorted().toList());
        }
        return candidate;
    }
}
