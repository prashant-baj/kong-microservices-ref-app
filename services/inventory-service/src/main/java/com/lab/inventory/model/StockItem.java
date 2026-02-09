package com.lab.inventory.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "stock_items")
public class StockItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "product_id", nullable = false, unique = true)
    private UUID productId;

    @Column(name = "quantity_available", nullable = false)
    private int quantityAvailable;

    @Column(name = "quantity_reserved", nullable = false)
    private int quantityReserved = 0;

    @Column(name = "last_updated")
    private Instant lastUpdated;

    @Version
    private Long version;

    protected StockItem() {}

    public StockItem(UUID productId, int quantityAvailable) {
        this.productId = productId;
        this.quantityAvailable = quantityAvailable;
    }

    public void reserveStock(int quantity) {
        if (quantity > quantityAvailable) {
            throw new IllegalArgumentException(
                    "Cannot reserve " + quantity + " units. Only " + quantityAvailable + " available.");
        }
        this.quantityAvailable -= quantity;
        this.quantityReserved += quantity;
    }

    public void releaseStock(int quantity) {
        this.quantityAvailable += quantity;
        this.quantityReserved -= quantity;
    }

    @PrePersist
    void prePersist() {
        lastUpdated = Instant.now();
    }

    @PreUpdate
    void preUpdate() {
        lastUpdated = Instant.now();
    }

    public UUID getId() { return id; }
    public UUID getProductId() { return productId; }
    public int getQuantityAvailable() { return quantityAvailable; }
    public int getQuantityReserved() { return quantityReserved; }
    public Instant getLastUpdated() { return lastUpdated; }
    public Long getVersion() { return version; }

    public void setQuantityAvailable(int quantityAvailable) { this.quantityAvailable = quantityAvailable; }
}
