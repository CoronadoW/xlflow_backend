package com.coronado.esflowix.controller;

import com.coronado.esflowix.dto.*;
import com.coronado.esflowix.model.Request;
import com.coronado.esflowix.model.Sale;
import com.coronado.esflowix.service.RequestService;
import com.coronado.esflowix.service.SaleService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/requests")
@RequiredArgsConstructor
//CorsConfig implements adsCorsMapping for Development (localhost:4200) and Production in the Server (https://xlflow.coronadodev.com)
//@CrossOrigin(origins = "http://localhost:4200")
@Validated
public class RequestController {

    private final RequestService requestService;
    private final SaleService saleService;

    @PostMapping
    public ResponseEntity<Request> createRequest(@Valid @RequestBody RequestDto requestDto) {
        Request request = requestService.createRequest(requestDto);
        return ResponseEntity.ok(request);
    }

    @PostMapping("/{requestId}/assign/{saleId}")
    public ResponseEntity<String> assignRequestToSale(@PathVariable Long requestId, @PathVariable Long saleId) {
        requestService.attachRequestToSale(requestId, saleId);
        return ResponseEntity.ok("Pedido asignado correctamente");
    }

    @GetMapping("/search/products")
    public ResponseEntity<List<String>> searchProducts(@RequestParam String query) {
        return ResponseEntity.ok(requestService.searchProductsByName(query));
    }

    @GetMapping("/product/price")
    public ResponseEntity<BigDecimal> getProductPrice(@RequestParam String productName) {
        return ResponseEntity.ok(requestService.getProductSalePrice(productName));
    }

    @GetMapping("/delivery/{date}")
    public ResponseEntity<List<Request>> getRequestsByDeliveryDate(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(requestService.getRequestsByDeliveryDate(date));
    }

    @GetMapping("/delivery/{date}/supplier")
    public ResponseEntity<Map<String, BigDecimal>> getSupplierSummary(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(requestService.getSupplierSummaryByDeliveryDate(date));
    }

    @GetMapping("/delivery/{date}/customers")
    public ResponseEntity<Map<String, BigDecimal>> getCustomerSummary(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(requestService.getCustomerSummaryByDeliveryDate(date));
    }

    @GetMapping("/delivery/{date}/profit")
    public ResponseEntity<BigDecimal> getProfit(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(requestService.calculateProfitByDeliveryDate(date));
    }

    // src/main/java/com/coronado/esflowix/controller/RequestController.java
    @GetMapping("/delivery/{date}/customer-details")
    public ResponseEntity<List<CustomerDetailDto>> getCustomerDetails(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(requestService.getCustomerDetailsByDeliveryDate(date));
    }

    @GetMapping("/delivery/{date}/export-supplier")
    public ResponseEntity<InputStreamResource> exportSupplierOrder(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {

        ByteArrayInputStream in = requestService.exportSupplierOrderToExcel(date);

        return ResponseEntity.ok()
                .header("Content-Disposition", "attachment; filename=pedido_proveedor_" + date + ".xlsx")
                .header("Content-Type", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                .body(new InputStreamResource(in));
    }

    @GetMapping("/delivery/{date}/supplier-payment")
    public ResponseEntity<BigDecimal> getSupplierPayment(
            @PathVariable @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(requestService.calculateSupplierPaymentByDeliveryDate(date));
    }

    // 🔥 NUEVO: Obtener un pedido por ID
    @GetMapping("/{id}")
    public ResponseEntity<Request> getRequestById(@PathVariable Long id) {
        return ResponseEntity.ok(requestService.getRequestById(id));
    }

    // 🔥 NUEVO: Actualizar un pedido existente
    @PutMapping("/{id}")
    public ResponseEntity<Request> updateRequest(
            @PathVariable Long id,
            @RequestBody RequestDto requestDto) {
        return ResponseEntity.ok(requestService.updateRequest(id, requestDto));
    }

    // 🔥 NUEVO: Eliminar un pedido
    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteRequest(@PathVariable Long id) {
        requestService.deleteRequest(id);
        return ResponseEntity.ok("Pedido eliminado correctamente");
    }
}