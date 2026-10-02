package com.coronado.esflowix.service;

import com.coronado.esflowix.dto.CustomerDetailDto;
import com.coronado.esflowix.dto.ProductDetailDto;
import com.coronado.esflowix.dto.RequestDto;
import com.coronado.esflowix.dto.RequestProdDto;
import com.coronado.esflowix.model.*;
import com.coronado.esflowix.repository.PriceListRepository;
import com.coronado.esflowix.repository.ProductPriceRepository;
import com.coronado.esflowix.repository.RequestRepository;
import com.coronado.esflowix.repository.SaleRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RequestService {

    private final RequestRepository requestRepository;
    private final ProductService productService;
    private final SaleRepository saleRepository;
    private final PriceListRepository priceListRepository;
    private final ProductPriceRepository productPriceRepository;
    private final SaleService saleService;

    // Constante para redondeo
    private static final BigDecimal ROUND_MULTIPLE = BigDecimal.valueOf(100);

    // ==================== MÉTODOS DE CREACIÓN Y ACTUALIZACIÓN ====================

    // Fusiona productos duplicados en un mapa (por nombre de producto) / Suma las cantidades y recalcula subtotales
    private Map<String, RequestProdDto> mergeProducts(List<RequestProdDto> products) {
        Map<String, RequestProdDto> merged = new LinkedHashMap<>();

        for (RequestProdDto dto : products) {
            String normalizedName = normalize(dto.getProductName());

            if (merged.containsKey(normalizedName)) {
                // 🔥 Ya existe → sumar cantidades
                RequestProdDto existing = merged.get(normalizedName);
                existing.setQuantity(existing.getQuantity().add(dto.getQuantity()));
            } else {
                // 🔥 Nuevo producto → agregar al mapa
                RequestProdDto copy = new RequestProdDto();
                copy.setProductName(dto.getProductName());
                copy.setQuantity(dto.getQuantity());
                merged.put(normalizedName, copy);
            }
        }

        return merged;
    }

    // Crea un pedido con productos fusionados
    @Transactional
    public Request createRequest(RequestDto requestDto) {
        Request request = new Request();
        request.setCustomerName(requestDto.getCustomerName());

        // 🔥 Obtener la lista de precios elegida (obligatoria)
        if (requestDto.getPriceListId() == null) {
            throw new RuntimeException("Debe seleccionar una lista de precios");
        }
        PriceList priceList = priceListRepository.findById(requestDto.getPriceListId())
                .orElseThrow(() -> new RuntimeException("Lista de precios no encontrada"));
        request.setPriceList(priceList);

        // Fusionar productos duplicados
        Map<String, RequestProdDto> mergedProducts = mergeProducts(requestDto.getRequestProdDtoList());

        List<RequestProduct> requestProducts = new ArrayList<>();
        BigDecimal totalRequest = BigDecimal.ZERO;

        for (RequestProdDto dto : mergedProducts.values()) {
            String normalizedName = normalize(dto.getProductName());
            Product product = productService.findByNormalizedName(normalizedName)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + dto.getProductName()));

            BigDecimal productPrice = productPriceRepository
                    .findByProductAndPriceList(product, priceList)
                    .map(ProductPrice::getPriceSale)
                    .orElseThrow(() -> new RuntimeException(
                            "El producto '" + product.getName() + "' no tiene precio en la lista '" + priceList.getName() + "'"
                    ));

            BigDecimal quantity = dto.getQuantity();
            BigDecimal totalByProduct = productPrice.multiply(quantity);

            RequestProduct rp = new RequestProduct();
            rp.setProductName(product.getName());
            rp.setProductPrice(productPrice);
            rp.setQuantity(quantity);
            rp.setTotalByReqProd(totalByProduct);
            rp.setRequest(request);

            requestProducts.add(rp);
            totalRequest = totalRequest.add(totalByProduct);
        }

        request.setReqProdsList(requestProducts);
        request.setTotalBySale(totalRequest);

        // 🔥 NUEVO: Buscar o crear la Sale para la fecha de entrega
        if (requestDto.getDeliveryDate() == null) {
            throw new RuntimeException("La fecha de entrega es obligatoria");
        }
        Sale sale = saleService.findOrCreateByDate(requestDto.getDeliveryDate());
        request.setSale(sale);

        // Guardar el request
        Request savedRequest = requestRepository.save(request);

        // 🔥 Recalcular total de la Sale
        saleService.recalculateSaleTotal(sale);

        return savedRequest;
    }

    //Actualiza un pedido con productos fusionados
    @Transactional
    public Request updateRequest(Long id, RequestDto requestDto) {
        Request request = requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));

        request.setCustomerName(requestDto.getCustomerName());

        // 🔥 Actualizar lista de precios si cambió
        if (requestDto.getPriceListId() != null) {
            PriceList priceList = priceListRepository.findById(requestDto.getPriceListId())
                    .orElseThrow(() -> new RuntimeException("Lista de precios no encontrada"));
            request.setPriceList(priceList);
        }

        PriceList priceList = request.getPriceList();
        if (priceList == null) {
            throw new RuntimeException("El pedido no tiene lista de precios asignada");
        }

        // 🔥 Limpiar productos antiguos
        request.getReqProdsList().clear();

        // Fusionar productos duplicados
        Map<String, RequestProdDto> mergedProducts = mergeProducts(requestDto.getRequestProdDtoList());

        List<RequestProduct> requestProducts = new ArrayList<>();
        BigDecimal totalRequest = BigDecimal.ZERO;

        for (RequestProdDto dto : mergedProducts.values()) {
            String normalizedName = normalize(dto.getProductName());
            Product product = productService.findByNormalizedName(normalizedName)
                    .orElseThrow(() -> new RuntimeException("Producto no encontrado: " + dto.getProductName()));

            BigDecimal productPrice = productPriceRepository
                    .findByProductAndPriceList(product, priceList)
                    .map(ProductPrice::getPriceSale)
                    .orElseThrow(() -> new RuntimeException(
                            "El producto '" + product.getName() + "' no tiene precio en la lista '" + priceList.getName() + "'"
                    ));

            BigDecimal quantity = dto.getQuantity();
            BigDecimal totalByProduct = productPrice.multiply(quantity);

            RequestProduct rp = new RequestProduct();
            rp.setProductName(product.getName());
            rp.setProductPrice(productPrice);
            rp.setQuantity(quantity);
            rp.setTotalByReqProd(totalByProduct);
            rp.setRequest(request);

            requestProducts.add(rp);
            totalRequest = totalRequest.add(totalByProduct);
        }

        request.getReqProdsList().addAll(requestProducts);
        request.setTotalBySale(totalRequest);

        // 🔥 Si cambió la fecha, reasignar a otra Sale (opcional)
        if (requestDto.getDeliveryDate() != null) {
            Sale currentSale = request.getSale();
            if (currentSale == null || !currentSale.getDeliveryDate().equals(requestDto.getDeliveryDate())) {
                Sale newSale = saleService.findOrCreateByDate(requestDto.getDeliveryDate());
                request.setSale(newSale);
            }
        }

        Request savedRequest = requestRepository.save(request);

        // 🔥 Recalcular total de la Sale
        if (savedRequest.getSale() != null) {
            saleService.recalculateSaleTotal(savedRequest.getSale());
        }

        return savedRequest;
    }
    // ==================== ASIGNACIÓN A VENTA ====================

    @Transactional
    public void attachRequestToSale(Long requestId, Long saleId) {
        Request request = requestRepository.findById(requestId)
                .orElseThrow(() -> new RuntimeException("Request no encontrada"));
        Sale sale = saleRepository.findById(saleId)
                .orElseThrow(() -> new RuntimeException("Sale no encontrada"));

        request.setSale(sale);
        requestRepository.save(request);
        saleService.recalculateSaleTotal(sale);
    }



    // ==================== CONSULTAS Y RESUMEN ====================

    @Transactional(readOnly = true)
    public Request getRequestById(Long id) {
        return requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));
    }

    @Transactional(readOnly = true)
    public List<Request> getRequestsByDeliveryDate(LocalDate deliveryDate) {
        List<Sale> sales = saleRepository.findByDeliveryDate(deliveryDate);
        return sales.stream()
                .flatMap(sale -> sale.getRequests().stream())
                .collect(Collectors.toList());
    }

    @Transactional
    public void deleteRequest(Long id) {
        Request request = requestRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Pedido no encontrado"));

        Sale sale = request.getSale();
        requestRepository.delete(request);

        if (sale != null) {
            saleService.recalculateSaleTotal(sale);
        }
    }

    // ==================== RESUMEN PROVEEDOR ====================

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getSupplierSummaryByDeliveryDate(LocalDate deliveryDate) {
        List<Sale> sales = saleRepository.findByDeliveryDate(deliveryDate);
        Map<String, BigDecimal> productQuantities = new HashMap<>();

        for (Sale sale : sales) {
            for (Request request : sale.getRequests()) {
                for (RequestProduct rp : request.getReqProdsList()) {
                    String productName = rp.getProductName();
                    BigDecimal quantity = rp.getQuantity();
                    productQuantities.merge(productName, quantity, BigDecimal::add);
                }
            }
        }

        return productQuantities.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    @Transactional(readOnly = true)
    public Map<String, Map<String, BigDecimal>> getSupplierOrderGroupedByCategory(LocalDate deliveryDate) {
        List<Sale> sales = saleRepository.findByDeliveryDate(deliveryDate);
        Map<String, Map<String, BigDecimal>> result = new TreeMap<>();

        for (Sale sale : sales) {
            for (Request request : sale.getRequests()) {
                for (RequestProduct rp : request.getReqProdsList()) {
                    String productName = rp.getProductName();
                    BigDecimal quantity = rp.getQuantity();

                    String normalizedName = normalize(productName);
                    Product product = productService.findByNormalizedName(normalizedName).orElse(null);
                    String category = (product != null && product.getCategory() != null)
                            ? product.getCategory()
                            : "SIN CATEGORÍA";

                    result.computeIfAbsent(category, k -> new TreeMap<>())
                            .merge(productName, quantity, BigDecimal::add);
                }
            }
        }

        return result;
    }

    @Transactional(readOnly = true)
    public BigDecimal calculateSupplierPaymentByDeliveryDate(LocalDate deliveryDate) {
        List<Sale> sales = saleRepository.findByDeliveryDate(deliveryDate);
        BigDecimal totalPayment = BigDecimal.ZERO;

        for (Sale sale : sales) {
            for (Request request : sale.getRequests()) {
                for (RequestProduct rp : request.getReqProdsList()) {
                    String normalizedName = normalize(rp.getProductName());
                    Product product = productService.findByNormalizedName(normalizedName)
                            .orElse(null);

                    if (product != null) {
                        BigDecimal quantity = rp.getQuantity();
                        totalPayment = totalPayment.add(product.getPricePurchase().multiply(quantity));
                    }
                }
            }
        }

        return totalPayment;
    }

    // ==================== RESUMEN CLIENTES ====================

    @Transactional(readOnly = true)
    public Map<String, BigDecimal> getCustomerSummaryByDeliveryDate(LocalDate deliveryDate) {
        List<Sale> sales = saleRepository.findByDeliveryDate(deliveryDate);
        Map<String, BigDecimal> customerTotals = new HashMap<>();

        for (Sale sale : sales) {
            for (Request request : sale.getRequests()) {
                String customerName = request.getCustomerName();
                BigDecimal total = request.getTotalBySale();
                customerTotals.merge(customerName, total, BigDecimal::add);
            }
        }

        return customerTotals.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByKey())
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new
                ));
    }

    @Transactional(readOnly = true)
    public List<CustomerDetailDto> getCustomerDetailsByDeliveryDate(LocalDate deliveryDate) {
        List<Sale> sales = saleRepository.findByDeliveryDate(deliveryDate);
        List<CustomerDetailDto> result = new ArrayList<>();

        for (Sale sale : sales) {
            for (Request request : sale.getRequests()) {
                CustomerDetailDto detail = new CustomerDetailDto();
                detail.setCustomerName(request.getCustomerName());
                detail.setTotal(request.getTotalBySale());

                // 🔥 NUEVO: nombre de la lista de precios
                if (request.getPriceList() != null) {
                    detail.setPriceListName(request.getPriceList().getName());
                } else {
                    detail.setPriceListName("Sin lista"); // Para pedidos viejos
                }

                List<ProductDetailDto> products = new ArrayList<>();
                for (RequestProduct rp : request.getReqProdsList()) {
                    ProductDetailDto pd = new ProductDetailDto();
                    pd.setProductName(rp.getProductName());
                    pd.setQuantity(rp.getQuantity());
                    pd.setUnitPrice(rp.getProductPrice());
                    pd.setSubtotal(rp.getTotalByReqProd());
                    products.add(pd);
                }
                detail.setProducts(products);
                result.add(detail);
            }
        }
        return result;
    }
    // ==================== GANANCIA ====================

    @Transactional(readOnly = true)
    public BigDecimal calculateProfitByDeliveryDate(LocalDate deliveryDate) {
        List<Sale> sales = saleRepository.findByDeliveryDate(deliveryDate);

        BigDecimal totalSalePrice = BigDecimal.ZERO;
        BigDecimal totalPurchasePrice = BigDecimal.ZERO;

        for (Sale sale : sales) {
            for (Request request : sale.getRequests()) {
                for (RequestProduct rp : request.getReqProdsList()) {
                    String normalizedName = normalize(rp.getProductName());
                    Product product = productService.findByNormalizedName(normalizedName)
                            .orElse(null);

                    if (product != null) {
                        BigDecimal quantity = rp.getQuantity();
                        // 🔥 Usar precio de venta del RequestProduct (ya tiene el de la lista elegida)
                        totalSalePrice = totalSalePrice.add(rp.getProductPrice().multiply(quantity));
                        // 🔥 Usar precio de compra sin redondear
                        totalPurchasePrice = totalPurchasePrice.add(product.getPricePurchase().multiply(quantity));
                    }
                }
            }
        }

        return totalSalePrice.subtract(totalPurchasePrice);
    }

    // ==================== BÚSQUEDA DE PRODUCTOS ====================

    @Transactional(readOnly = true)
    public List<String> searchProductsByName(String query) {
        return productService.getAll().stream()
                .filter(p -> p.isAvailable())
                .map(Product::getName)
                .filter(name -> name.toLowerCase().contains(query.toLowerCase()))
                .limit(10)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public BigDecimal getProductSalePrice(String productName) {
        String normalizedName = normalize(productName);
        return productService.findByNormalizedName(normalizedName)
                .map(Product::getPriceSale)
                .orElseThrow(() -> new RuntimeException("Producto no encontrado"));
    }

    // ==================== EXPORTAR EXCEL PROVEEDOR ====================

    public ByteArrayInputStream exportSupplierOrderToExcel(LocalDate deliveryDate) {
        Map<String, Map<String, BigDecimal>> groupedData = getSupplierOrderGroupedByCategory(deliveryDate);

        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Pedido Proveedor");
            int rowIdx = 0;

            // FUENTES
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);

            Font categoryFont = workbook.createFont();
            categoryFont.setBold(true);
            categoryFont.setFontHeightInPoints((short) 12);

            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerFont.setFontHeightInPoints((short) 11);

            // ESTILOS
            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);

            CellStyle categoryStyle = workbook.createCellStyle();
            categoryStyle.setFont(categoryFont);
            categoryStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            categoryStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            categoryStyle.setBorderTop(BorderStyle.MEDIUM);
            categoryStyle.setBorderBottom(BorderStyle.MEDIUM);
            categoryStyle.setBorderLeft(BorderStyle.MEDIUM);
            categoryStyle.setBorderRight(BorderStyle.THIN);

            CellStyle categoryStyleRight = workbook.createCellStyle();
            categoryStyleRight.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            categoryStyleRight.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            categoryStyleRight.setBorderTop(BorderStyle.MEDIUM);
            categoryStyleRight.setBorderBottom(BorderStyle.MEDIUM);
            categoryStyleRight.setBorderLeft(BorderStyle.THIN);
            categoryStyleRight.setBorderRight(BorderStyle.MEDIUM);

            CellStyle headerStyle = workbook.createCellStyle();
            headerStyle.setFont(headerFont);
            headerStyle.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            headerStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            headerStyle.setBorderTop(BorderStyle.MEDIUM);
            headerStyle.setBorderBottom(BorderStyle.MEDIUM);
            headerStyle.setBorderLeft(BorderStyle.THIN);
            headerStyle.setBorderRight(BorderStyle.THIN);

            CellStyle productStyle = workbook.createCellStyle();
            productStyle.setBorderTop(BorderStyle.THIN);
            productStyle.setBorderBottom(BorderStyle.THIN);
            productStyle.setBorderLeft(BorderStyle.THIN);
            productStyle.setBorderRight(BorderStyle.THIN);

            CellStyle lastProductStyle = workbook.createCellStyle();
            lastProductStyle.setBorderTop(BorderStyle.THIN);
            lastProductStyle.setBorderBottom(BorderStyle.MEDIUM);
            lastProductStyle.setBorderLeft(BorderStyle.THIN);
            lastProductStyle.setBorderRight(BorderStyle.THIN);

            CellStyle quantityStyle = workbook.createCellStyle();
            quantityStyle.setBorderTop(BorderStyle.THIN);
            quantityStyle.setBorderBottom(BorderStyle.THIN);
            quantityStyle.setBorderLeft(BorderStyle.THIN);
            quantityStyle.setBorderRight(BorderStyle.THIN);
            DataFormat format = workbook.createDataFormat();
            quantityStyle.setDataFormat(format.getFormat("#,##0"));

            CellStyle totalLabelStyle = workbook.createCellStyle();
            totalLabelStyle.setFont(categoryFont);
            totalLabelStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            totalLabelStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalLabelStyle.setBorderTop(BorderStyle.MEDIUM);
            totalLabelStyle.setBorderBottom(BorderStyle.MEDIUM);
            totalLabelStyle.setBorderLeft(BorderStyle.MEDIUM);
            totalLabelStyle.setBorderRight(BorderStyle.THIN);

            CellStyle totalValueStyle = workbook.createCellStyle();
            totalValueStyle.setFont(categoryFont);
            totalValueStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            totalValueStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            totalValueStyle.setBorderTop(BorderStyle.MEDIUM);
            totalValueStyle.setBorderBottom(BorderStyle.MEDIUM);
            totalValueStyle.setBorderLeft(BorderStyle.THIN);
            totalValueStyle.setBorderRight(BorderStyle.MEDIUM);

            // HEADER - Título
            Row titleRow = sheet.createRow(rowIdx++);
            titleRow.setHeight((short) 800);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("PEDIDO A PROVEEDOR");
            titleCell.setCellStyle(titleStyle);
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 1));

            // Fecha de entrega
            Row dateRow = sheet.createRow(rowIdx++);
            dateRow.setHeight((short) 500);
            Cell dateLabelCell = dateRow.createCell(0);
            dateLabelCell.setCellValue("Fecha de Entrega:");
            dateLabelCell.setCellStyle(categoryStyle);

            Cell dateValueCell = dateRow.createCell(1);
            dateValueCell.setCellValue(deliveryDate.toString());
            dateValueCell.setCellStyle(productStyle);

            rowIdx++;

            // ENCABEZADOS DE TABLA
            Row headerRow = sheet.createRow(rowIdx++);
            headerRow.setHeight((short) 500);

            Cell headerQuantity = headerRow.createCell(0);
            headerQuantity.setCellValue("CANTIDAD");
            headerQuantity.setCellStyle(headerStyle);

            Cell headerProduct = headerRow.createCell(1);
            headerProduct.setCellValue("PRODUCTO");
            headerProduct.setCellStyle(headerStyle);

            // DATOS AGRUPADOS POR CATEGORÍA
            for (Map.Entry<String, Map<String, BigDecimal>> categoryEntry : groupedData.entrySet()) {
                String category = categoryEntry.getKey();
                Map<String, BigDecimal> products = categoryEntry.getValue();

                Row catRow = sheet.createRow(rowIdx++);
                catRow.setHeight((short) 400);

                Cell catCellLeft = catRow.createCell(0);
                catCellLeft.setCellValue(category.toUpperCase());
                catCellLeft.setCellStyle(categoryStyle);

                Cell catCellRight = catRow.createCell(1);
                catCellRight.setCellValue("");
                catCellRight.setCellStyle(categoryStyleRight);

                sheet.addMergedRegion(new CellRangeAddress(rowIdx - 1, rowIdx - 1, 0, 1));

                int productCount = products.size();
                int productIndex = 0;
                for (Map.Entry<String, BigDecimal> productEntry : products.entrySet()) {
                    productIndex++;
                    Row productRow = sheet.createRow(rowIdx++);
                    productRow.setHeight((short) 350);

                    CellStyle currentProductStyle = (productIndex == productCount)
                            ? lastProductStyle
                            : productStyle;

                    Cell quantityCell = productRow.createCell(0);
                    quantityCell.setCellValue(productEntry.getValue().doubleValue());
                    quantityCell.setCellStyle(currentProductStyle);

                    Cell productCell = productRow.createCell(1);
                    productCell.setCellValue(productEntry.getKey());
                    productCell.setCellStyle(currentProductStyle);
                }

                rowIdx++;
            }

            sheet.setColumnWidth(0, 4000);
            sheet.setColumnWidth(1, 12000);

            int totalProducts = 0;
            for (Map<String, BigDecimal> products : groupedData.values()) {
                totalProducts += products.size();
            }

            Row totalRow = sheet.createRow(rowIdx++);
            totalRow.setHeight((short) 400);

            Cell totalLabelCell = totalRow.createCell(0);
            totalLabelCell.setCellValue("TOTAL PRODUCTOS:");
            totalLabelCell.setCellStyle(totalLabelStyle);

            Cell totalValueCell = totalRow.createCell(1);
            totalValueCell.setCellValue(totalProducts);
            totalValueCell.setCellStyle(totalValueStyle);

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (Exception e) {
            throw new RuntimeException("Error generando Excel del proveedor", e);
        }
    }

    // ==================== UTILIDADES ====================

    private String normalize(String text) {
        return text.toLowerCase()
                .trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[^a-z0-9 ]", "");
    }
}