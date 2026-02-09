package com.lab.inventory.dto;

import com.lab.inventory.model.StockItem;
import java.time.Instant;
import java.util.UUID;

public record StockItemResponse(
        UUID id,
        UUID productId,
        int quantityAvailable,
        int quantityReserved,
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
