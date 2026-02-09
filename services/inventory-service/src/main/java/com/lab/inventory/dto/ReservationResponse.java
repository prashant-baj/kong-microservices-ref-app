package com.lab.inventory.dto;

import com.lab.inventory.model.Reservation;
import com.lab.inventory.model.ReservationStatus;
import java.time.Instant;
import java.util.UUID;

public record ReservationResponse(
        UUID id,
        UUID stockItemId,
        UUID orderId,
        int quantity,
        ReservationStatus status,
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
