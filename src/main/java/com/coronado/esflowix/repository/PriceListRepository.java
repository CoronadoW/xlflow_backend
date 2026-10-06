package com.coronado.esflowix.repository;

import com.coronado.esflowix.model.PriceList;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface PriceListRepository extends JpaRepository<PriceList, Long> {

    List<PriceList> findAllByActiveTrue();
    Optional<PriceList> findByName(String name);
    Optional<PriceList> findByMargin(BigDecimal margin);
    Optional<PriceList> findById(Long id);
    boolean existsByName(String name);

}
