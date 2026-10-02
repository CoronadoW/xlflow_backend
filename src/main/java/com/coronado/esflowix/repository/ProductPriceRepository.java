package com.coronado.esflowix.repository;

import com.coronado.esflowix.model.PriceList;
import com.coronado.esflowix.model.Product;
import com.coronado.esflowix.model.ProductPrice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductPriceRepository extends JpaRepository<ProductPrice, Long> {

    Optional<ProductPrice> findByProductAndPriceList(Product product, PriceList priceList);
    List<ProductPrice> findAllByPriceList(PriceList priceList);
    List<ProductPrice> findAllByProduct(Product product);

}
