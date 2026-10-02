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
public class RequestDto {

    private String customerName;

    private Long priceListId;

    private LocalDate deliveryDate;

    private List<RequestProdDto> requestProdDtoList;

}