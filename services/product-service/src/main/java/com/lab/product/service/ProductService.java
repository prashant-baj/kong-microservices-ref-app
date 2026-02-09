package com.lab.product.service;

import com.lab.product.dto.CreateProductRequest;
import com.lab.product.exception.DuplicateProductException;
import com.lab.product.exception.ProductNotFoundException;
import com.lab.product.model.Product;
import com.lab.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional
    public Product createProduct(CreateProductRequest request) {
        if (productRepository.existsByName(request.name())) {
            throw new DuplicateProductException(request.name());
        }
        Product product = new Product(
                request.name(),
                request.description(),
                request.price(),
                request.category()
        );
        return productRepository.save(product);
    }

    public Product getProduct(UUID id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }
}
