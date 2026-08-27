package com.example.ecommerce.user.controller;

import com.example.ecommerce.user.dto.AddressRequest;
import com.example.ecommerce.user.dto.AddressResponse;
import com.example.ecommerce.user.dto.ProfileResponse;
import com.example.ecommerce.user.dto.ProfileUpsertRequest;
import com.example.ecommerce.user.exception.InvalidIdentityException;
import com.example.ecommerce.user.service.UserProfileService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users/me")
public class UserProfileController {

    private final UserProfileService service;

    public UserProfileController(UserProfileService service) {
        this.service = service;
    }

    @GetMapping
    public ProfileResponse getProfile(@AuthenticationPrincipal Jwt jwt) {
        return service.getProfile(subject(jwt));
    }

    @PutMapping
    public ProfileResponse upsertProfile(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ProfileUpsertRequest request
    ) {
        return service.upsertProfile(subject(jwt), request);
    }

    @PostMapping("/addresses")
    public ResponseEntity<AddressResponse> addAddress(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody AddressRequest request
    ) {
        AddressResponse response = service.addAddress(subject(jwt), request);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{addressId}")
                .buildAndExpand(response.id())
                .toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PutMapping("/addresses/{addressId}")
    public AddressResponse updateAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId,
            @Valid @RequestBody AddressRequest request
    ) {
        return service.updateAddress(subject(jwt), addressId, request);
    }

    @DeleteMapping("/addresses/{addressId}")
    public ResponseEntity<Void> deleteAddress(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID addressId
    ) {
        service.deleteAddress(subject(jwt), addressId);
        return ResponseEntity.noContent().build();
    }

    private UUID subject(Jwt jwt) {
        try {
            return UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException exception) {
            throw new InvalidIdentityException();
        }
    }
}
