package com.lab.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record ReserveStockRequest(
        @NotNull(message = "Product ID is required")
        UUID productId,

        @NotNull(message = "Order ID is required")
        UUID orderId,

        @Positive(message = "Quantity must be positive")
        int quantity
) {}
