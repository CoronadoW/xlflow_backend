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
public class ProductDto {

    private Long id;
    private String name;
    private String normalizedName;
    private String category;
    private BigDecimal pricePurchase;
    private BigDecimal priceSale;      // 🔥 Precio de venta de la lista elegida
    private boolean available;
    private Long priceListId;          // 🔥 ID de la lista usada
    private String priceListName;      // 🔥 Nombre de la lista
}
