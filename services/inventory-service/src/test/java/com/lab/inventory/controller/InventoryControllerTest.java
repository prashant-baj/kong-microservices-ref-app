package com.lab.inventory.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.inventory.dto.AddStockRequest;
import com.lab.inventory.dto.ReserveStockRequest;
import com.lab.inventory.exception.GlobalExceptionHandler;
import com.lab.inventory.exception.InsufficientStockException;
import com.lab.inventory.model.Reservation;
import com.lab.inventory.model.StockItem;
import com.lab.inventory.service.InventoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(InventoryController.class)
@Import(GlobalExceptionHandler.class)
class InventoryControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private InventoryService inventoryService;

    @Test
    void should_Return201_When_StockReserved() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        var request = new ReserveStockRequest(productId, orderId, 5);
        var reservation = new Reservation(UUID.randomUUID(), orderId, 5);

        when(inventoryService.reserveStock(any())).thenReturn(reservation);

        mockMvc.perform(post("/api/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.orderId").value(orderId.toString()))
                .andExpect(jsonPath("$.quantity").value(5))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void should_Return409_When_InsufficientStock() throws Exception {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        var request = new ReserveStockRequest(productId, orderId, 100);

        when(inventoryService.reserveStock(any()))
                .thenThrow(new InsufficientStockException(productId, 100, 5));

        mockMvc.perform(post("/api/inventory/reservations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void should_Return201_When_StockAdded() throws Exception {
        UUID productId = UUID.randomUUID();
        var request = new AddStockRequest(productId, 50);
        var stockItem = new StockItem(productId, 50);

        when(inventoryService.addStock(any())).thenReturn(stockItem);

        mockMvc.perform(post("/api/inventory/stock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.quantityAvailable").value(50));
    }

    @Test
    void should_Return200_When_StockQueried() throws Exception {
        UUID productId = UUID.randomUUID();
        var stockItem = new StockItem(productId, 75);

        when(inventoryService.getStockForProduct(productId)).thenReturn(stockItem);

        mockMvc.perform(get("/api/inventory/stock/{productId}", productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.productId").value(productId.toString()))
                .andExpect(jsonPath("$.quantityAvailable").value(75));
    }
}
