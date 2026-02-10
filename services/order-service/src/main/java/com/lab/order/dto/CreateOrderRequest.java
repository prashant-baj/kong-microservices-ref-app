package com.lab.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

@Schema(description = "Request body to create a new order")
public record CreateOrderRequest(
        @Schema(description = "Customer name", example = "John Doe")
        @NotBlank(message = "Customer name is required")
        String customerName,

        @Schema(description = "List of line items in the order")
        @NotEmpty(message = "Order must have at least one item")
        List<@Valid OrderLineItemRequest> items
) {}
