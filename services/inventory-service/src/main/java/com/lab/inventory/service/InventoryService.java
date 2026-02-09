package com.lab.inventory.service;

import com.lab.inventory.dto.AddStockRequest;
import com.lab.inventory.dto.ReserveStockRequest;
import com.lab.inventory.exception.InsufficientStockException;
import com.lab.inventory.exception.ReservationNotFoundException;
import com.lab.inventory.exception.StockItemNotFoundException;
import com.lab.inventory.model.Reservation;
import com.lab.inventory.model.ReservationStatus;
import com.lab.inventory.model.StockItem;
import com.lab.inventory.repository.ReservationRepository;
import com.lab.inventory.repository.StockItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class InventoryService {

    private final StockItemRepository stockItemRepository;
    private final ReservationRepository reservationRepository;

    public InventoryService(StockItemRepository stockItemRepository,
                            ReservationRepository reservationRepository) {
        this.stockItemRepository = stockItemRepository;
        this.reservationRepository = reservationRepository;
    }

    @Transactional
    public StockItem addStock(AddStockRequest request) {
        Optional<StockItem> existing = stockItemRepository.findByProductId(request.productId());
        if (existing.isPresent()) {
            StockItem stockItem = existing.get();
            stockItem.setQuantityAvailable(stockItem.getQuantityAvailable() + request.quantity());
            return stockItemRepository.save(stockItem);
        }
        StockItem stockItem = new StockItem(request.productId(), request.quantity());
        return stockItemRepository.save(stockItem);
    }

    @Transactional
    public Reservation reserveStock(ReserveStockRequest request) {
        StockItem stockItem = stockItemRepository.findByProductId(request.productId())
                .orElseThrow(() -> new StockItemNotFoundException(request.productId()));

        // Idempotency check: if reservation already exists for this stockItem + orderId, return it
        Optional<Reservation> existingReservation =
                reservationRepository.findByStockItemIdAndOrderId(stockItem.getId(), request.orderId());
        if (existingReservation.isPresent()) {
            return existingReservation.get();
        }

        try {
            stockItem.reserveStock(request.quantity());
        } catch (IllegalArgumentException e) {
            throw new InsufficientStockException(
                    request.productId(), request.quantity(), stockItem.getQuantityAvailable());
        }

        stockItemRepository.save(stockItem);

        Reservation reservation = new Reservation(stockItem.getId(), request.orderId(), request.quantity());
        return reservationRepository.save(reservation);
    }

    @Transactional
    public Reservation cancelReservation(UUID reservationId) {
        Reservation reservation = reservationRepository.findById(reservationId)
                .orElseThrow(() -> new ReservationNotFoundException(reservationId));

        StockItem stockItem = stockItemRepository.findById(reservation.getStockItemId())
                .orElseThrow(() -> new StockItemNotFoundException(reservation.getStockItemId()));

        stockItem.releaseStock(reservation.getQuantity());
        reservation.setStatus(ReservationStatus.CANCELLED);

        stockItemRepository.save(stockItem);
        reservationRepository.save(reservation);

        return reservation;
    }

    public StockItem getStockForProduct(UUID productId) {
        return stockItemRepository.findByProductId(productId)
                .orElseThrow(() -> new StockItemNotFoundException(productId));
    }
}
