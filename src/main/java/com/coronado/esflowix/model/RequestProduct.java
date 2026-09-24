package com.coronado.esflowix.model;

import com.fasterxml.jackson.annotation.JsonBackReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@NoArgsConstructor
@AllArgsConstructor
@Setter
@Getter

@Entity
public class RequestProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    private Long id;
    private String productName;
    private BigDecimal productPrice;
    private BigDecimal quantity;
    private BigDecimal totalByReqProd;

    @JsonBackReference(value = "request-product")
    @ManyToOne
    @JoinColumn(name = "request_id")
    private Request request;
}
