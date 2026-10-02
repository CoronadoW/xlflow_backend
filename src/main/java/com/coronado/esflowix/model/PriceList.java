package com.coronado.esflowix.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "price_list")
public class PriceList {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; //Name list for this applied margin

    @Column(nullable = false, precision = 5, scale = 4)
    private BigDecimal margin; //Margen : 0.35, 0.25

    @Column(nullable = false)
    private boolean active = true;
}
