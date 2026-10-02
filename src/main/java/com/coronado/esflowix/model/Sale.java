package com.coronado.esflowix.model;

import com.fasterxml.jackson.annotation.JsonManagedReference;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor

@Entity
@Table(name = "sale")
public class Sale {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private LocalDate deliveryDate;

    @Column(nullable = false)
    private BigDecimal total;

    @JsonManagedReference(value = "sale-request")
    @OneToMany(mappedBy = "sale", cascade = CascadeType.ALL)
    private List<Request> requests = new ArrayList<>();
}


