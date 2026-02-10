package com.lab.order.dto;

import com.lab.order.model.Order;
import com.lab.order.model.OrderStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Schema(description = "Order response with full details and line items")
public record OrderResponse(
        @Schema(description = "Order unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Customer name", example = "John Doe")
        String customerName,

        @Schema(description = "Current order status", example = "PENDING")
        OrderStatus status,

        @Schema(description = "Total order amount", example = "1999.98")
        BigDecimal totalAmount,

        @Schema(description = "Order creation timestamp")
        Instant createdAt,

        @Schema(description = "List of items in the order")
        List<LineItemResponse> items
) {
    public static OrderResponse from(Order order) {
        List<LineItemResponse> items = order.getLineItems().stream()
                .map(LineItemResponse::from)
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getCustomerName(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                items
        );
    }
}
