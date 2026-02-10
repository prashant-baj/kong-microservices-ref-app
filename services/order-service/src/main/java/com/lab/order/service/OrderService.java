package com.lab.order.service;

import com.lab.order.client.InventoryServiceClient;
import com.lab.order.client.InventoryServiceClient.ReservationInfo;
import com.lab.order.client.ProductServiceClient;
import com.lab.order.client.ProductServiceClient.ProductInfo;
import com.lab.order.dto.CreateOrderRequest;
import com.lab.order.exception.OrderCreationException;
import com.lab.order.exception.OrderNotFoundException;
import com.lab.order.model.LineItem;
import com.lab.order.model.Order;
import com.lab.order.model.OrderStatus;
import com.lab.order.repository.OrderRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ProductServiceClient productServiceClient;
    private final InventoryServiceClient inventoryServiceClient;

    public OrderService(OrderRepository orderRepository,
                        ProductServiceClient productServiceClient,
                        InventoryServiceClient inventoryServiceClient) {
        this.orderRepository = orderRepository;
        this.productServiceClient = productServiceClient;
        this.inventoryServiceClient = inventoryServiceClient;
    }

    @Transactional
    public Order createOrder(CreateOrderRequest request) {
        Order order = new Order(request.customerName(), OrderStatus.CREATED);

        // Look up each product and build line items
        for (var item : request.items()) {
            ProductInfo product = productServiceClient.getProduct(item.productId());
            LineItem lineItem = new LineItem(
                    product.id(),
                    product.name(),
                    item.quantity(),
                    product.price()
            );
            order.addLineItem(lineItem);
        }

        // Calculate total amount from line items
        BigDecimal totalAmount = order.getLineItems().stream()
                .map(LineItem::getSubtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        order.setTotalAmount(totalAmount);

        orderRepository.save(order);

        // Try to reserve inventory for each line item
        List<ReservationInfo> successfulReservations = new ArrayList<>();
        try {
            for (LineItem lineItem : order.getLineItems()) {
                ReservationInfo reservation = inventoryServiceClient.reserveStock(
                        lineItem.getProductId(),
                        order.getId(),
                        lineItem.getQuantity()
                );
                successfulReservations.add(reservation);
            }
        } catch (Exception ex) {
            log.warn("Inventory reservation failed, compensating {} successful reservations",
                    successfulReservations.size(), ex);
            // Compensate: cancel all successful reservations
            for (ReservationInfo reservation : successfulReservations) {
                try {
                    inventoryServiceClient.cancelReservation(reservation.id());
                } catch (Exception cancelEx) {
                    log.error("Failed to cancel reservation {}", reservation.id(), cancelEx);
                }
            }
            order.setStatus(OrderStatus.FAILED);
            orderRepository.save(order);
            throw new OrderCreationException(
                    "Failed to reserve inventory for order " + order.getId(), ex);
        }

        order.setStatus(OrderStatus.CONFIRMED);
        orderRepository.save(order);
        return order;
    }

    public Order getOrder(UUID id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAllWithLineItems();
    }
}
