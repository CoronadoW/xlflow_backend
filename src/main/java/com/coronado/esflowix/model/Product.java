package com.coronado.esflowix.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@NoArgsConstructor
@AllArgsConstructor
@Getter
@Setter

@Entity
@Table (name = "product")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    @Column(unique = true, nullable = false)
    private String normalizedName;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private BigDecimal pricePurchase;

    @Column(nullable = false)
    private BigDecimal priceSale;

    @Column(nullable = false)
    private boolean available;

    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductPrice> productPrices = new ArrayList<>();

}
