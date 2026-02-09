package com.lab.product.exception;

public class DuplicateProductException extends RuntimeException {

    public DuplicateProductException(String name) {
        super("Product with name '" + name + "' already exists");
    }
}
