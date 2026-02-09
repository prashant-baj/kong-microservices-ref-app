package com.lab.inventory.repository;

import com.lab.inventory.model.StockItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface StockItemRepository extends JpaRepository<StockItem, UUID> {

    Optional<StockItem> findByProductId(UUID productId);

    boolean existsByProductId(UUID productId);
}
