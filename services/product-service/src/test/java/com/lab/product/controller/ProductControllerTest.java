package com.lab.product.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lab.product.dto.CreateProductRequest;
import com.lab.product.exception.GlobalExceptionHandler;
import com.lab.product.exception.ProductNotFoundException;
import com.lab.product.model.Product;
import com.lab.product.service.ProductService;
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

@WebMvcTest(ProductController.class)
@Import(GlobalExceptionHandler.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductService productService;

    @Test
    void should_Return201WithLocation_When_ProductCreated() throws Exception {
        var request = new CreateProductRequest("Laptop", "A powerful laptop", new BigDecimal("999.99"), "Electronics");
        var product = new Product("Laptop", "A powerful laptop", new BigDecimal("999.99"), "Electronics");

        when(productService.createProduct(any())).thenReturn(product);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.name").value("Laptop"))
                .andExpect(jsonPath("$.price").value(999.99))
                .andExpect(jsonPath("$.category").value("Electronics"));
    }

    @Test
    void should_Return400_When_NameIsBlank() throws Exception {
        var request = new CreateProductRequest("", "Description", new BigDecimal("10.00"), "Category");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void should_Return400_When_PriceIsNegative() throws Exception {
        var request = new CreateProductRequest("Laptop", "Desc", new BigDecimal("-5.00"), "Electronics");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void should_Return200_When_ProductFound() throws Exception {
        UUID id = UUID.randomUUID();
        var product = new Product("Laptop", "A laptop", new BigDecimal("999.99"), "Electronics");

        when(productService.getProduct(id)).thenReturn(product);

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Laptop"));
    }

    @Test
    void should_Return404_When_ProductNotFound() throws Exception {
        UUID id = UUID.randomUUID();

        when(productService.getProduct(id)).thenThrow(new ProductNotFoundException(id));

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").exists());
    }

    @Test
    void should_ReturnAllProducts() throws Exception {
        var laptop = new Product("Laptop", "A laptop", new BigDecimal("999.99"), "Electronics");
        var phone = new Product("Phone", "A phone", new BigDecimal("699.99"), "Electronics");

        when(productService.getAllProducts()).thenReturn(List.of(laptop, phone));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].name").value("Laptop"))
                .andExpect(jsonPath("$[1].name").value("Phone"));
    }
}
