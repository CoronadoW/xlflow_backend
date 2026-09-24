// src/main/java/com/coronado/esflowix/dto/SupplierOrderDto.java
package com.coronado.esflowix.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.math.BigDecimal;
import java.util.Map;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class SupplierOrderDto {
    private String deliveryDate;
    private Map<String, Map<String, BigDecimal>> categories; // Categoría -> (Producto -> Cantidad)
    private BigDecimal totalItems;
}