package com.example.ecommerce.web;

import com.example.ecommerce.domain.Product;
import com.example.ecommerce.repository.ProductRepository;
import com.example.ecommerce.service.NotFoundException;
import com.example.ecommerce.web.dto.ProductRequest;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductRepository products;

    public ProductController(ProductRepository products) {
        this.products = products;
    }

    @GetMapping
    public List<Product> list() {
        return products.findAll();
    }

    @GetMapping("/{sku}")
    public Product get(@PathVariable String sku) {
        return products.findBySku(sku).orElseThrow(() -> new NotFoundException("Product not found: " + sku));
    }

    /** Creates a product, or updates name/price/stock if the SKU exists. */
    @PutMapping
    @Transactional
    public Product upsert(@Valid @RequestBody ProductRequest request) {
        Product product = products.findBySku(request.sku())
                .orElseGet(() -> new Product(request.sku(), request.name(), request.price(), 0));
        product.setName(request.name());
        product.setPrice(request.price());
        product.setAvailableQuantity(request.availableQuantity());
        return products.save(product);
    }
}
