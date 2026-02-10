package com.lab.inventory.dto;

import com.lab.inventory.model.Reservation;
import com.lab.inventory.model.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;

@Schema(description = "Stock reservation response with details")
public record ReservationResponse(
        @Schema(description = "Reservation unique identifier", example = "550e8400-e29b-41d4-a716-446655440000")
        UUID id,

        @Schema(description = "Stock item unique identifier", example = "660e8400-e29b-41d4-a716-446655440000")
        UUID stockItemId,

        @Schema(description = "Order unique identifier", example = "770e8400-e29b-41d4-a716-446655440000")
        UUID orderId,

        @Schema(description = "Quantity reserved", example = "5")
        int quantity,

        @Schema(description = "Current reservation status", example = "CONFIRMED")
        ReservationStatus status,

        @Schema(description = "Reservation creation timestamp")
        Instant createdAt
) {
    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.getId(),
                reservation.getStockItemId(),
                reservation.getOrderId(),
                reservation.getQuantity(),
                reservation.getStatus(),
                reservation.getCreatedAt()
        );
    }
}
