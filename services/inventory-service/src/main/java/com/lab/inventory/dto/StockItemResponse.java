package com.lab.inventory.dto;

import com.lab.inventory.model.StockItem;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Stock item response with inventory details")
public record StockItemResponse(
        @Schema(description = "Stock item unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Product unique identifier", example = "660e8400-e29b-41d4-a716-446655440000")
        UUID productId,

        @Schema(description = "Quantity of stock available", example = "150")
        int quantityAvailable,

        @Schema(description = "Quantity of stock reserved for orders", example = "25")
        int quantityReserved,

        @Schema(description = "Last update timestamp")
        Instant lastUpdated
) {
    public static StockItemResponse from(StockItem stockItem) {
        return new StockItemResponse(
                stockItem.getId(),
                stockItem.getProductId(),
                stockItem.getQuantityAvailable(),
                stockItem.getQuantityReserved(),
                stockItem.getLastUpdated()
        );
    }
}
