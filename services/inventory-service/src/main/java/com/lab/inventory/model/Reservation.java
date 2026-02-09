package com.lab.inventory.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "reservations", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"stock_item_id", "order_id"})
})
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "stock_item_id", nullable = false)
    private UUID stockItemId;

    @Column(name = "order_id", nullable = false)
    private UUID orderId;

    @Column(nullable = false)
    private int quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Reservation() {}

    public Reservation(UUID stockItemId, UUID orderId, int quantity) {
        this.stockItemId = stockItemId;
        this.orderId = orderId;
        this.quantity = quantity;
        this.status = ReservationStatus.PENDING;
    }

    @PrePersist
    void prePersist() {
        createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getStockItemId() { return stockItemId; }
    public UUID getOrderId() { return orderId; }
    public int getQuantity() { return quantity; }
    public ReservationStatus getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }

    public void setStatus(ReservationStatus status) { this.status = status; }
}
