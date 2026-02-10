package com.lab.inventory.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

@Schema(description = "Request body to add stock for a product")
public record AddStockRequest(
        @Schema(description = "Product unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotNull(message = "Product ID is required")
        UUID productId,

        @Schema(description = "Quantity to add", example = "100")
        @Positive(message = "Quantity must be positive")
        int quantity
) {}
