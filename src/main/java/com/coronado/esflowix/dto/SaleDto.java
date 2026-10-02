package com.coronado.esflowix.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

    @NotBlank(message = "DeliveryDate must not be blank")
    private LocalDate deliveryDate;
    @NotEmpty(message = "Request ids list must not be empty")
    @Valid
    private List<Long> requestIds;
}
