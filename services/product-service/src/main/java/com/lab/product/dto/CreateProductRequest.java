package com.lab.product.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

@Schema(description = "Request body to create a new product")
public record CreateProductRequest(
        @Schema(description = "Product name", example = "Laptop")
        @NotBlank(message = "Name is required")
        String name,

        @Schema(description = "Product description", example = "High-performance laptop for professionals")
        String description,

        @Schema(description = "Product price", example = "999.99")
        @NotNull(message = "Price is required")
        @Positive(message = "Price must be positive")
        BigDecimal price,

        @Schema(description = "Product category", example = "Electronics")
        @NotBlank(message = "Category is required")
        String category
) {}
