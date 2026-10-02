package com.coronado.esflowix.service;

import com.coronado.esflowix.dto.SaleDto;
import com.coronado.esflowix.model.Request;
import com.coronado.esflowix.model.Sale;
import com.coronado.esflowix.repository.RequestRepository;
import com.coronado.esflowix.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository saleRepo;
    private final RequestRepository requestRepository;
    private final RequestService requestService;

    @Transactional
    public Sale createSale(SaleDto dto) {
        Sale sale = new Sale();
        sale.setDeliveryDate(dto.getDeliveryDate());
        sale.setTotal(BigDecimal.ZERO);
        return saleRepo.save(sale);
    }

    @Transactional
    public void attachRequestToSale(Long saleId, Long requestId) {
        Sale sale = saleRepo.findById(saleId)
                .orElseThrow(() -> new RuntimeException("Sale no encontrada"));
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request no encontrada"));

        request.setSale(sale);
        requestRepository.save(request);
        recalculateSaleTotal(sale);
    }

    @Transactional
    private void recalculateSaleTotal(Sale sale) {
        BigDecimal total = sale.getRequests()
                .stream()
                .map(Request::getTotalBySale)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        sale.setTotal(total);
        saleRepo.save(sale);
    }

    @Transactional(readOnly = true)
    public List<Sale> findByDeliveryDate(LocalDate date) {
        return saleRepo.findByDeliveryDate(date);
    }

    @Transactional(readOnly = true)
    public Map<String, Object> getDeliverySummary(LocalDate date) {
        Map<String, Object> summary = new HashMap<>();

        List<Sale> sales = saleRepo.findByDeliveryDate(date);
        int totalRequests = sales.stream()
                .mapToInt(sale -> sale.getRequests().size())
                .sum();

        BigDecimal totalSales = sales.stream()
                .map(Sale::getTotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Map<String, BigDecimal> customerTotals = new HashMap<>();
        for (Sale sale : sales) {
            for (Request request : sale.getRequests()) {
                customerTotals.merge(
                        request.getCustomerName(),
                        request.getTotalBySale(),
                        BigDecimal::add
                );
            }
        }

        summary.put("deliveryDate", date);
        summary.put("totalSales", totalSales);
        summary.put("totalRequests", totalRequests);
        summary.put("totalCustomers", customerTotals.size());
        summary.put("customers", customerTotals);
        summary.put("sales", sales);

        return summary;
    }
}