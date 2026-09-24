package com.coronado.esflowix.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor

public class SaleDto {

    private LocalDate deliveryDate;
    private List<Long> requestIds;
}
