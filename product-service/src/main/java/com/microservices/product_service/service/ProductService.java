package com.microservices.product_service.service;

import com.microservices.product_service.entity.Product;

import java.util.List;

public interface ProductService {
    Product createProduct(Product product);
    List<Product> getAllProducts();
}
