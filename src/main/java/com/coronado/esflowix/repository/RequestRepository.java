package com.coronado.esflowix.repository;

import com.coronado.esflowix.model.Request;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RequestRepository extends JpaRepository<Request, Long> {


    List<Request> findAllBySaleId(Long saleId);
}
