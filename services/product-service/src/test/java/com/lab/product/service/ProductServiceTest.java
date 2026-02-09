package com.lab.product.service;

import com.lab.product.dto.CreateProductRequest;
import com.lab.product.exception.DuplicateProductException;
import com.lab.product.exception.ProductNotFoundException;
import com.lab.product.model.Product;
import com.lab.product.repository.ProductRepository;
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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    private ProductService productService;

    @BeforeEach
    void setUp() {
        productService = new ProductService(productRepository);
    }

    @Test
    void should_CreateProduct_When_NameIsUnique() {
        var request = new CreateProductRequest("Laptop", "A powerful laptop", new BigDecimal("999.99"), "Electronics");

        when(productRepository.existsByName("Laptop")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Product result = productService.createProduct(request);

        assertThat(result.getName()).isEqualTo("Laptop");
        assertThat(result.getPrice()).isEqualByComparingTo(new BigDecimal("999.99"));
        assertThat(result.getCategory()).isEqualTo("Electronics");

        ArgumentCaptor<Product> captor = ArgumentCaptor.forClass(Product.class);
        verify(productRepository).save(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Laptop");
    }

    @Test
    void should_ThrowDuplicateException_When_NameAlreadyExists() {
        var request = new CreateProductRequest("Laptop", "Another laptop", new BigDecimal("499.99"), "Electronics");

        when(productRepository.existsByName("Laptop")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(DuplicateProductException.class)
                .hasMessageContaining("Laptop");

        verify(productRepository, never()).save(any());
    }

    @Test
    void should_ReturnProduct_When_IdExists() {
        UUID id = UUID.randomUUID();
        Product product = new Product("Laptop", "A laptop", new BigDecimal("999.99"), "Electronics");

        when(productRepository.findById(id)).thenReturn(Optional.of(product));

        Product result = productService.getProduct(id);

        assertThat(result.getName()).isEqualTo("Laptop");
    }

    @Test
    void should_ThrowNotFoundException_When_IdDoesNotExist() {
        UUID id = UUID.randomUUID();

        when(productRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProduct(id))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining(id.toString());
    }

    @Test
    void should_ReturnAllProducts_When_ProductsExist() {
        Product laptop = new Product("Laptop", "A laptop", new BigDecimal("999.99"), "Electronics");
        Product phone = new Product("Phone", "A phone", new BigDecimal("699.99"), "Electronics");

        when(productRepository.findAll()).thenReturn(List.of(laptop, phone));

        List<Product> result = productService.getAllProducts();

        assertThat(result).hasSize(2);
        assertThat(result).extracting(Product::getName).containsExactly("Laptop", "Phone");
    }
}
