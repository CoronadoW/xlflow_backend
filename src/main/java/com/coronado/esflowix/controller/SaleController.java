package com.coronado.esflowix.controller;

import com.coronado.esflowix.dto.SaleDto;
import com.coronado.esflowix.model.Sale;
import com.coronado.esflowix.service.SaleService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sales")
@CrossOrigin(origins = "http://localhost:4200")
public class SaleController {

    private final SaleService saleService;

    public SaleController(SaleService saleService) {
        this.saleService = saleService;
    }

    @PostMapping
    public ResponseEntity<Sale> createSale(@RequestBody SaleDto dto) {
        Sale sale = saleService.createSale(dto);
        return ResponseEntity.ok(sale);
    }

    @GetMapping("/delivery/{date}")
    public ResponseEntity<List<Sale>> getByDeliveryDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(saleService.findByDeliveryDate(date));
    }

    @GetMapping("/delivery/{date}/summary")
    public ResponseEntity<Map<String, Object>> getFullDeliverySummary(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Map<String, Object> summary = saleService.getDeliverySummary(date);
        return ResponseEntity.ok(summary);
    }
}