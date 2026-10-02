package com.coronado.esflowix.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class ProductQuantityDto {
    @NotBlank(message = "Product name must not be blank")
    private String productName;
    @NotBlank(message = "Quantity must not be blank")
    private BigDecimal quantity;
}