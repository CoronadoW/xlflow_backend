package com.coronado.esflowix.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerSummaryDto {
    private String customerName;
    private BigDecimal totalAmount;
}