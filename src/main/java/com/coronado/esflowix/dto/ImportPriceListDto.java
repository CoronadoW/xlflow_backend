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
public class ImportPriceListDto {

    private String  name;        //Consumidor Final , Negocio , etc
    private BigDecimal margin;   //0.35, 0.25 , etc

}
