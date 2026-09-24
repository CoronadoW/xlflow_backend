package com.coronado.esflowix.repository;

import com.coronado.esflowix.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository< Product , Long > {

    //Optional<Product> findByNameIgnoreCase(String name);

    Optional<Product> findFirstByNameIgnoreCase(String name);

    Optional<Product> findByNormalizedName (String normalizedName);

    List<Product> findAllByAvailableTrueOrderByCategoryAscNameAsc();
}
