package com.example.ecommerce.repository;

import com.example.ecommerce.domain.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    Optional<Product> findBySku(String sku);

    /** Atomically decrements stock; returns 0 if there was not enough stock. */
    @Modifying
    @Query("update Product p set p.availableQuantity = p.availableQuantity - :qty "
            + "where p.sku = :sku and p.availableQuantity >= :qty")
    int decrementStock(@Param("sku") String sku, @Param("qty") int qty);

    @Modifying
    @Query("update Product p set p.availableQuantity = p.availableQuantity + :qty where p.sku = :sku")
    int incrementStock(@Param("sku") String sku, @Param("qty") int qty);
}
