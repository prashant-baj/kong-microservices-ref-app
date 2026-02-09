package com.lab.order.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.order.dto.CreateOrderRequest;
import com.lab.order.dto.OrderLineItemRequest;
import com.lab.order.exception.GlobalExceptionHandler;
import com.lab.order.exception.OrderNotFoundException;
import com.lab.order.model.LineItem;
import com.lab.order.model.Order;
import com.lab.order.model.OrderStatus;
import com.lab.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private OrderService orderService;

    @Test
    void should_Return201WithLocation_When_OrderCreated() throws Exception {
        UUID productId = UUID.randomUUID();
        var request = new CreateOrderRequest("Alice", List.of(
                new OrderLineItemRequest(productId, 2)
        ));

        Order order = new Order("Alice", OrderStatus.CONFIRMED);
        order.setTotalAmount(new BigDecimal("1999.98"));
        order.addLineItem(new LineItem(productId, "Laptop", 2, new BigDecimal("999.99")));

        when(orderService.createOrder(any())).thenReturn(order);

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.customerName").value("Alice"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.totalAmount").value(1999.98))
                .andExpect(jsonPath("$.items.length()").value(1));
    }

    @Test
    void should_Return200_When_OrderFound() throws Exception {
        UUID orderId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();

        Order order = new Order("Bob", OrderStatus.CONFIRMED);
        order.setTotalAmount(new BigDecimal("79.99"));
        order.addLineItem(new LineItem(productId, "Keyboard", 1, new BigDecimal("79.99")));

        when(orderService.getOrder(orderId)).thenReturn(order);

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customerName").value("Bob"))
                .andExpect(jsonPath("$.status").value("CONFIRMED"));
    }

    @Test
    void should_Return404_When_OrderNotFound() throws Exception {
        UUID orderId = UUID.randomUUID();

        when(orderService.getOrder(orderId)).thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void should_Return400_When_RequestInvalid() throws Exception {
        // Missing customer name and empty items list
        var request = new CreateOrderRequest("", List.of());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }
}
