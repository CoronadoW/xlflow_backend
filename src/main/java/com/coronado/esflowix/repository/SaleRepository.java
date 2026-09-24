package com.coronado.esflowix.repository;

import com.coronado.esflowix.model.Sale;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    List<Sale> findByDeliveryDate(LocalDate date);
}
