package com.lab.inventory.exception;

import java.util.UUID;

public class StockItemNotFoundException extends RuntimeException {

    public StockItemNotFoundException(UUID productId) {
        super("Stock item not found for product: " + productId);
    }
}
