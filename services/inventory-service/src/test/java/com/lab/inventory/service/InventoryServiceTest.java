package com.lab.inventory.service;

import com.lab.inventory.dto.AddStockRequest;
import com.lab.inventory.dto.ReserveStockRequest;
import com.lab.inventory.exception.InsufficientStockException;
import com.lab.inventory.exception.StockItemNotFoundException;
import com.lab.inventory.model.Reservation;
import com.lab.inventory.model.ReservationStatus;
import com.lab.inventory.model.StockItem;
import com.lab.inventory.repository.ReservationRepository;
import com.lab.inventory.repository.StockItemRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InventoryServiceTest {

    @Mock
    private StockItemRepository stockItemRepository;

    @Mock
    private ReservationRepository reservationRepository;

    private InventoryService inventoryService;

    @BeforeEach
    void setUp() {
        inventoryService = new InventoryService(stockItemRepository, reservationRepository);
    }

    @Test
    void should_ReserveStock_When_SufficientQuantityAvailable() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        StockItem stockItem = new StockItem(productId, 100);
        var request = new ReserveStockRequest(productId, orderId, 10);

        when(stockItemRepository.findByProductId(productId)).thenReturn(Optional.of(stockItem));
        when(reservationRepository.findByStockItemIdAndOrderId(any(), eq(orderId))).thenReturn(Optional.empty());
        when(stockItemRepository.save(any(StockItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reservation result = inventoryService.reserveStock(request);

        assertThat(result.getOrderId()).isEqualTo(orderId);
        assertThat(result.getQuantity()).isEqualTo(10);
        assertThat(result.getStatus()).isEqualTo(ReservationStatus.PENDING);

        ArgumentCaptor<StockItem> stockCaptor = ArgumentCaptor.forClass(StockItem.class);
        verify(stockItemRepository).save(stockCaptor.capture());
        assertThat(stockCaptor.getValue().getQuantityAvailable()).isEqualTo(90);
        assertThat(stockCaptor.getValue().getQuantityReserved()).isEqualTo(10);
    }

    @Test
    void should_ThrowInsufficientStockException_When_NotEnoughStock() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        StockItem stockItem = new StockItem(productId, 5);
        var request = new ReserveStockRequest(productId, orderId, 10);

        when(stockItemRepository.findByProductId(productId)).thenReturn(Optional.of(stockItem));
        when(reservationRepository.findByStockItemIdAndOrderId(any(), eq(orderId))).thenReturn(Optional.empty());

        assertThatThrownBy(() -> inventoryService.reserveStock(request))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");

        verify(reservationRepository, never()).save(any());
    }

    @Test
    void should_ReturnExistingReservation_When_DuplicateRequest() {
        UUID productId = UUID.randomUUID();
        UUID orderId = UUID.randomUUID();
        UUID stockItemId = UUID.randomUUID();
        StockItem stockItem = new StockItem(productId, 100);
        Reservation existingReservation = new Reservation(stockItemId, orderId, 10);
        var request = new ReserveStockRequest(productId, orderId, 10);

        when(stockItemRepository.findByProductId(productId)).thenReturn(Optional.of(stockItem));
        when(reservationRepository.findByStockItemIdAndOrderId(any(), eq(orderId)))
                .thenReturn(Optional.of(existingReservation));

        Reservation result = inventoryService.reserveStock(request);

        assertThat(result).isSameAs(existingReservation);
        verify(stockItemRepository, never()).save(any());
        verify(reservationRepository, never()).save(any());
    }

    @Test
    void should_ReleaseStock_When_ReservationCancelled() {
        UUID reservationId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        StockItem stockItem = new StockItem(productId, 90);
        // Simulate that 10 units were previously reserved
        stockItem.reserveStock(10);
        // Now stockItem has 80 available, 10 reserved

        Reservation reservation = new Reservation(stockItem.getId(), UUID.randomUUID(), 10);

        when(reservationRepository.findById(reservationId)).thenReturn(Optional.of(reservation));
        when(stockItemRepository.findById(reservation.getStockItemId())).thenReturn(Optional.of(stockItem));
        when(stockItemRepository.save(any(StockItem.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(reservationRepository.save(any(Reservation.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Reservation result = inventoryService.cancelReservation(reservationId);

        assertThat(result.getStatus()).isEqualTo(ReservationStatus.CANCELLED);

        ArgumentCaptor<StockItem> stockCaptor = ArgumentCaptor.forClass(StockItem.class);
        verify(stockItemRepository).save(stockCaptor.capture());
        assertThat(stockCaptor.getValue().getQuantityAvailable()).isEqualTo(90);
        assertThat(stockCaptor.getValue().getQuantityReserved()).isEqualTo(0);
    }

    @Test
    void should_AddStock_When_NewProduct() {
        UUID productId = UUID.randomUUID();
        var request = new AddStockRequest(productId, 50);

        when(stockItemRepository.findByProductId(productId)).thenReturn(Optional.empty());
        when(stockItemRepository.save(any(StockItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockItem result = inventoryService.addStock(request);

        assertThat(result.getProductId()).isEqualTo(productId);
        assertThat(result.getQuantityAvailable()).isEqualTo(50);

        ArgumentCaptor<StockItem> captor = ArgumentCaptor.forClass(StockItem.class);
        verify(stockItemRepository).save(captor.capture());
        assertThat(captor.getValue().getProductId()).isEqualTo(productId);
        assertThat(captor.getValue().getQuantityAvailable()).isEqualTo(50);
    }

    @Test
    void should_AddToExistingStock_When_ProductAlreadyExists() {
        UUID productId = UUID.randomUUID();
        StockItem existingItem = new StockItem(productId, 30);
        var request = new AddStockRequest(productId, 20);

        when(stockItemRepository.findByProductId(productId)).thenReturn(Optional.of(existingItem));
        when(stockItemRepository.save(any(StockItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StockItem result = inventoryService.addStock(request);

        assertThat(result.getQuantityAvailable()).isEqualTo(50);

        ArgumentCaptor<StockItem> captor = ArgumentCaptor.forClass(StockItem.class);
        verify(stockItemRepository).save(captor.capture());
        assertThat(captor.getValue().getQuantityAvailable()).isEqualTo(50);
    }
}
