package com.lab.inventory.controller;

import com.lab.inventory.dto.AddStockRequest;
import com.lab.inventory.dto.ReservationResponse;
import com.lab.inventory.dto.ReserveStockRequest;
import com.lab.inventory.dto.StockItemResponse;
import com.lab.inventory.model.Reservation;
import com.lab.inventory.model.StockItem;
import com.lab.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/inventory")
@Tag(name = "Inventory", description = "APIs for managing inventory and stock")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @PostMapping("/stock")
    @Operation(summary = "Add stock", description = "Adds inventory stock for a product")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Stock added successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StockItemResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<StockItemResponse> addStock(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Stock addition request",
                    required = true,
                    content = @Content(schema = @Schema(implementation = AddStockRequest.class))
            )
            @Valid @RequestBody AddStockRequest request) {
        StockItem stockItem = inventoryService.addStock(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(StockItemResponse.from(stockItem));
    }

    @PostMapping("/reservations")
    @Operation(summary = "Reserve stock", description = "Reserves inventory stock for a product")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Stock reserved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request parameters"),
            @ApiResponse(responseCode = "409", description = "Insufficient stock available"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ReservationResponse> reserveStock(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Stock reservation request",
                    required = true,
                    content = @Content(schema = @Schema(implementation = ReserveStockRequest.class))
            )
            @Valid @RequestBody ReserveStockRequest request) {
        Reservation reservation = inventoryService.reserveStock(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ReservationResponse.from(reservation));
    }

    @DeleteMapping("/reservations/{id}")
    @Operation(summary = "Cancel reservation", description = "Cancels an existing stock reservation")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reservation cancelled successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ReservationResponse.class))),
            @ApiResponse(responseCode = "404", description = "Reservation not found"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<ReservationResponse> cancelReservation(
            @PathVariable
            @Parameter(description = "Reservation ID", example = "550e8400-e29b-41d4-a716-446655440000")
            UUID id) {
        Reservation reservation = inventoryService.cancelReservation(id);
        return ResponseEntity.ok(ReservationResponse.from(reservation));
    }

    @GetMapping("/stock/{productId}")
    @Operation(summary = "Get stock by product", description = "Retrieves stock information for a specific product")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Stock information retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = StockItemResponse.class))),
            @ApiResponse(responseCode = "404", description = "Stock not found for product"),
            @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    public ResponseEntity<StockItemResponse> getStockForProduct(
            @PathVariable
            @Parameter(description = "Product ID", example = "550e8400-e29b-41d4-a716-446655440000")
            UUID productId) {
        StockItem stockItem = inventoryService.getStockForProduct(productId);
        return ResponseEntity.ok(StockItemResponse.from(stockItem));
    }
}
