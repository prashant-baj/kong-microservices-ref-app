package com.lab.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

@Schema(description = "Request body to reserve stock for an order")
public record ReserveStockRequest(
        @Schema(description = "Product unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotNull(message = "Product ID is required")
        UUID productId,

        @Schema(description = "Order unique identifier", example = "660e8400-e29b-41d4-a716-446655440000")
        @NotNull(message = "Order ID is required")
        UUID orderId,

        @Schema(description = "Quantity to reserve", example = "5")
        @Positive(message = "Quantity must be positive")
        int quantity
) {}
