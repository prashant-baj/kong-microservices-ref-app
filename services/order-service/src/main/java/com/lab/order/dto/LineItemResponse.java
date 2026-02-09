package com.lab.order.dto;

import com.lab.order.model.LineItem;
import java.math.BigDecimal;
import java.util.UUID;

public record LineItemResponse(
        UUID id,
        UUID productId,
        String productName,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {
    public static LineItemResponse from(LineItem lineItem) {
        return new LineItemResponse(
                lineItem.getId(),
                lineItem.getProductId(),
                lineItem.getProductName(),
                lineItem.getQuantity(),
                lineItem.getUnitPrice(),
                lineItem.getSubtotal()
        );
    }
}
