package com.lab.order.service;

import com.lab.order.client.InventoryServiceClient;
import com.lab.order.client.InventoryServiceClient.ReservationInfo;
import com.lab.order.client.ProductServiceClient;
import com.lab.order.client.ProductServiceClient.ProductInfo;
import com.lab.order.dto.CreateOrderRequest;
import com.lab.order.dto.OrderLineItemRequest;
import com.lab.order.exception.OrderCreationException;
import com.lab.order.exception.OrderNotFoundException;
import com.lab.order.model.Order;
import com.lab.order.model.OrderStatus;
import com.lab.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private InventoryServiceClient inventoryServiceClient;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, productServiceClient, inventoryServiceClient);

        when(orderRepository.save(any(Order.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void should_CreateOrder_When_AllProductsExistAndStockAvailable() {
        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();

        var request = new CreateOrderRequest("Alice", List.of(
                new OrderLineItemRequest(productId1, 2),
                new OrderLineItemRequest(productId2, 1)
        ));

        when(productServiceClient.getProduct(productId1))
                .thenReturn(new ProductInfo(productId1, "Laptop", new BigDecimal("999.99")));
        when(productServiceClient.getProduct(productId2))
                .thenReturn(new ProductInfo(productId2, "Mouse", new BigDecimal("29.99")));

        when(inventoryServiceClient.reserveStock(eq(productId1), any(), eq(2)))
                .thenReturn(new ReservationInfo(UUID.randomUUID(), productId1, 2));
        when(inventoryServiceClient.reserveStock(eq(productId2), any(), eq(1)))
                .thenReturn(new ReservationInfo(UUID.randomUUID(), productId2, 1));

        Order result = orderService.createOrder(request);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("2029.97");

        verify(inventoryServiceClient).reserveStock(eq(productId1), any(), eq(2));
        verify(inventoryServiceClient).reserveStock(eq(productId2), any(), eq(1));
    }

    @Test
    void should_FailOrder_When_InventoryReservationFails() {
        UUID productId1 = UUID.randomUUID();
        UUID productId2 = UUID.randomUUID();

        var request = new CreateOrderRequest("Bob", List.of(
                new OrderLineItemRequest(productId1, 1),
                new OrderLineItemRequest(productId2, 3)
        ));

        when(productServiceClient.getProduct(productId1))
                .thenReturn(new ProductInfo(productId1, "Keyboard", new BigDecimal("79.99")));
        when(productServiceClient.getProduct(productId2))
                .thenReturn(new ProductInfo(productId2, "Monitor", new BigDecimal("399.99")));

        UUID reservationId1 = UUID.randomUUID();

        when(inventoryServiceClient.reserveStock(eq(productId1), any(), eq(1)))
                .thenReturn(new ReservationInfo(reservationId1, productId1, 1));
        when(inventoryServiceClient.reserveStock(eq(productId2), any(), eq(3)))
                .thenThrow(new RuntimeException("Insufficient stock"));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(OrderCreationException.class);

        verify(inventoryServiceClient).cancelReservation(reservationId1);
    }

    @Test
    void should_CalculateTotalAmount_From_LineItems() {
        UUID productId = UUID.randomUUID();

        var request = new CreateOrderRequest("Charlie",
                List.of(new OrderLineItemRequest(productId, 3)));

        when(productServiceClient.getProduct(productId))
                .thenReturn(new ProductInfo(productId, "Cable", new BigDecimal("12.50")));

        when(inventoryServiceClient.reserveStock(eq(productId), any(), eq(3)))
                .thenReturn(new ReservationInfo(UUID.randomUUID(), productId, 3));

        Order result = orderService.createOrder(request);

        assertThat(result.getTotalAmount()).isEqualByComparingTo("37.50");
    }
}
