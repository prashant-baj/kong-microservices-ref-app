package com.lab.product.repository;

import com.lab.product.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ProductRepositoryIntegrationTest {

    @SuppressWarnings("resource")
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("product_db")
            .withUsername("postgres")
            .withPassword("postgres");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private ProductRepository productRepository;

    @Test
    void should_PersistAndRetrieveProduct() {
        Product product = new Product("Laptop", "A powerful laptop", new BigDecimal("999.99"), "Electronics");

        Product saved = productRepository.save(product);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();

        Optional<Product> found = productRepository.findById(saved.getId());
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Laptop");
    }

    @Test
    void should_FindProductByName() {
        productRepository.save(new Product("Phone", "A smartphone", new BigDecimal("699.99"), "Electronics"));

        Optional<Product> found = productRepository.findByName("Phone");

        assertThat(found).isPresent();
        assertThat(found.get().getPrice()).isEqualByComparingTo(new BigDecimal("699.99"));
    }

    @Test
    void should_ReturnTrue_When_ProductNameExists() {
        productRepository.save(new Product("Tablet", "A tablet", new BigDecimal("399.99"), "Electronics"));

        assertThat(productRepository.existsByName("Tablet")).isTrue();
        assertThat(productRepository.existsByName("Nonexistent")).isFalse();
    }
}
