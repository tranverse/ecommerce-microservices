package com.example.ecommerce.product.dto;

import com.example.ecommerce.product.domain.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateProductRequest(
        @NotBlank
        @Size(max = 200)
        String name,

        @Size(max = 2000)
        String description,

        @NotNull
        @DecimalMin(value = "0.01")
        @Digits(integer = 17, fraction = 2)
        BigDecimal price,

        @NotBlank
        @Pattern(regexp = "^[A-Za-z]{3}$", message = "must be a three-letter ISO currency code")
        String currency,

        @NotNull
        ProductStatus status,

        @NotNull
        @PositiveOrZero
        Long version
) {
}
