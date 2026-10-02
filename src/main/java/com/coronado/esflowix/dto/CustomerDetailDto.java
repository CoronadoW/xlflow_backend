// src/main/java/com/coronado/esflowix/dto/CustomerDetailDto.java
package com.coronado.esflowix.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CustomerDetailDto {

    private String customerName;
    private BigDecimal total;
    private String priceListName;
    private List<ProductDetailDto> products;
}