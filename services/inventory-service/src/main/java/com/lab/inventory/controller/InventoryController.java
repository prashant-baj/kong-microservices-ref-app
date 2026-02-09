package com.lab.inventory.controller;

import com.lab.inventory.dto.AddStockRequest;
import com.lab.inventory.dto.ReservationResponse;
import com.lab.inventory.dto.ReserveStockRequest;
import com.lab.inventory.dto.StockItemResponse;
import com.lab.inventory.model.Reservation;
import com.lab.inventory.model.StockItem;
import com.lab.inventory.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/stock")
    public ResponseEntity<StockItemResponse> addStock(@Valid @RequestBody AddStockRequest request) {
        StockItem stockItem = inventoryService.addStock(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(StockItemResponse.from(stockItem));
    }

    @PostMapping("/reservations")
    public ResponseEntity<ReservationResponse> reserveStock(@Valid @RequestBody ReserveStockRequest request) {
        Reservation reservation = inventoryService.reserveStock(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReservationResponse.from(reservation));
    }

    @DeleteMapping("/reservations/{id}")
    public ResponseEntity<ReservationResponse> cancelReservation(@PathVariable UUID id) {
        Reservation reservation = inventoryService.cancelReservation(id);
        return ResponseEntity.ok(ReservationResponse.from(reservation));
    }

    @GetMapping("/stock/{productId}")
    public ResponseEntity<StockItemResponse> getStockForProduct(@PathVariable UUID productId) {
        StockItem stockItem = inventoryService.getStockForProduct(productId);
        return ResponseEntity.ok(StockItemResponse.from(stockItem));
    }
}
