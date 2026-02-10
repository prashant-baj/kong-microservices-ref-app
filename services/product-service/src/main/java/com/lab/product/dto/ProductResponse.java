package com.lab.product.dto;

import com.lab.product.model.Product;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Product response with full details")
public record ProductResponse(
        @Schema(description = "Product unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Product name", example = "Laptop")
        String name,

        @Schema(description = "Product description", example = "High-performance laptop for professionals")
        String description,

        @Schema(description = "Product price", example = "999.99")
        BigDecimal price,

        @Schema(description = "Product category", example = "Electronics")
        String category,

        @Schema(description = "Creation timestamp")
        Instant createdAt,

        @Schema(description = "Last update timestamp")
        Instant updatedAt
) {
    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getCategory(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
