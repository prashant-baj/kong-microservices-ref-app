package com.lab.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;

@Schema(description = "Request body for a line item in an order")
public record OrderLineItemRequest(
        @Schema(description = "Product unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
        @NotNull(message = "Product ID is required")
        UUID productId,

        @Schema(description = "Quantity of the product", example = "2")
        @Positive(message = "Quantity must be positive")
        int quantity
) {}
