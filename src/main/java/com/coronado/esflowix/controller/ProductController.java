package com.coronado.esflowix.controller;

import com.coronado.esflowix.dto.ImportPriceListDto;
import com.coronado.esflowix.model.Product;
import com.coronado.esflowix.service.ProductService;
import com.coronado.esflowix.service.RequestService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
@Validated
@CrossOrigin(origins = "http://localhost:4200")
public class ProductController {

    private final ProductService productService;
    private final RequestService requestService;
    private final ObjectMapper objectMapper = new ObjectMapper();


    @PostMapping("/import")
    public ResponseEntity<Map<String, String>> importExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam("priceLists") String priceListsJson) {
        try {
            List<ImportPriceListDto> configs = objectMapper.readValue(
                    priceListsJson,
                    new TypeReference<List<ImportPriceListDto>>() {}
            );

            productService.importExcel(file, configs);

            Map<String, String> response = new HashMap<>();
            response.put("message", "Productos importados correctamente");
            response.put("status", "success");
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, String> response = new HashMap<>();
            response.put("message", "Error al importar: " + e.getMessage());
            response.put("status", "error");
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/export/excel")
    public ResponseEntity<InputStreamResource> exportExcel() {
        ByteArrayInputStream in = productService.exportProductsToExcel();
        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=productos.xlsx")
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(new InputStreamResource(in));
    }

    @GetMapping("/available")
    public ResponseEntity<List<Product>> getAvailableProducts() {
        return ResponseEntity.ok(productService.getAll().stream()
                .filter(Product::isAvailable)
                .toList());
    }

    @GetMapping
    public ResponseEntity<List<Product>> getAll() {
        return ResponseEntity.ok(productService.getAll());
    }

    @GetMapping("/delivery/{date}/supplier")
    public Map<String, BigDecimal> getSupplierSummary(@PathVariable LocalDate date) {
        return requestService.getSupplierSummaryByDeliveryDate(date);
    }

    @GetMapping("/delivery/{date}/customers")
    public Map<String, BigDecimal> getCustomerSummary(@PathVariable LocalDate date) {
        return requestService.getCustomerSummaryByDeliveryDate(date);
    }

    @GetMapping("/delivery/{date}/profit")
    public BigDecimal getProfit(@PathVariable LocalDate date) {
        return requestService.calculateProfitByDeliveryDate(date);
    }
}