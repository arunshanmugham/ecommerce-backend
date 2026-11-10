package com.example.ecommerce.config;

import com.example.ecommerce.domain.Product;
import com.example.ecommerce.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/** Seeds a small catalogue on first start so orders can be placed right away. */
@Component
@ConditionalOnProperty(name = "app.seed-products", havingValue = "true")
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final ProductRepository products;

    public DataSeeder(ProductRepository products) {
        this.products = products;
    }

    @Override
    public void run(String... args) {
        if (products.count() > 0) {
            return;
        }
        products.saveAll(List.of(
                new Product("LAPTOP-001", "Ultrabook 14\"", new BigDecimal("1199.00"), 10),
                new Product("PHONE-001", "Smartphone X", new BigDecimal("699.00"), 25),
                new Product("HEADPHONES-001", "Noise-cancelling headphones", new BigDecimal("199.99"), 50),
                new Product("MOUSE-001", "Wireless mouse", new BigDecimal("24.50"), 100),
                new Product("MONITOR-001", "27\" 4K monitor (low stock)", new BigDecimal("349.00"), 2)));
        log.info("Seeded {} products", products.count());
    }
}
