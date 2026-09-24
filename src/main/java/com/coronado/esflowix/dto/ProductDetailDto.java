// src/main/java/com/coronado/esflowix/dto/ProductDetailDto.java
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
public class ProductDetailDto {
    private String productName;
    private BigDecimal quantity;
    private BigDecimal unitPrice;
    private BigDecimal subtotal;
}