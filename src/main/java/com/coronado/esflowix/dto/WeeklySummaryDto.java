package com.coronado.esflowix.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class WeeklySummaryDto {
    private LocalDate deliveryDate;
    private BigDecimal totalSales;
    private BigDecimal totalProfit;
    private int totalCustomers;
    private int totalRequests;
}