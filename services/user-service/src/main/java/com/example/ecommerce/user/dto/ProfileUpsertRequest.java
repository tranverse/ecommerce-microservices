package com.example.ecommerce.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ProfileUpsertRequest(
        @NotBlank @Size(min = 2, max = 120) String displayName,
        @Pattern(regexp = "^\\+[1-9]\\d{7,14}$", message = "must use E.164 format") String phone
) {
}
