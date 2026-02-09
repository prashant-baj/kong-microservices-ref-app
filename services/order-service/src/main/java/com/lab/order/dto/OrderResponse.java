package com.lab.order.dto;

import com.lab.order.model.Order;
import com.lab.order.model.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record OrderResponse(
        UUID id,
        String customerName,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
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
