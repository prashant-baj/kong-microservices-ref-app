package com.lab.order.dto;

import com.lab.order.model.LineItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.UUID;

@Schema(description = "Line item response with product and pricing details")
public record LineItemResponse(
        @Schema(description = "Line item unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Product unique identifier", example = "660e8400-e29b-41d4-a716-446655440000")
        UUID productId,

        @Schema(description = "Product name", example = "Laptop")
        String productName,

        @Schema(description = "Quantity ordered", example = "2")
        int quantity,

        @Schema(description = "Unit price of the product", example = "999.99")
        BigDecimal unitPrice,

        @Schema(description = "Subtotal for this line item (quantity * unitPrice)", example = "1999.98")
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
