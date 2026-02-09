package com.lab.inventory.repository;

import com.lab.inventory.model.Reservation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReservationRepository extends JpaRepository<Reservation, UUID> {

    Optional<Reservation> findByStockItemIdAndOrderId(UUID stockItemId, UUID orderId);
}
