package com.lab.inventory.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

public record AddStockRequest(
        @NotNull(message = "Product ID is required")
        UUID productId,

        @Positive(message = "Quantity must be positive")
        int quantity
) {}
