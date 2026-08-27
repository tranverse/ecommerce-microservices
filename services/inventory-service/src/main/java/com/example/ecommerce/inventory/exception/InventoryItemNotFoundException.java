package com.example.ecommerce.inventory.exception;

import org.springframework.http.HttpStatus;

import java.util.UUID;

public class InventoryItemNotFoundException extends ApiException {

    public InventoryItemNotFoundException(UUID productId) {
        super(HttpStatus.NOT_FOUND, ErrorCode.INVENTORY_ITEM_NOT_FOUND,
                "Inventory item for product '%s' was not found".formatted(productId));
    }
}
