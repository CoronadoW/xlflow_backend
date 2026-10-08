package com.coronado.esflowix.service;

import com.coronado.esflowix.dto.ImportPriceListDto;
import com.coronado.esflowix.dto.ProductDto;
import com.coronado.esflowix.model.PriceList;
import com.coronado.esflowix.model.Product;

import com.coronado.esflowix.model.ProductPrice;
import com.coronado.esflowix.repository.PriceListRepository;
import com.coronado.esflowix.repository.ProductPriceRepository;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.coronado.esflowix.repository.ProductRepository;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ProductService {

    private final ProductRepository productRepository;
    private final PriceListRepository priceListRepository;
    private final ProductPriceRepository productPriceRepository;

    // Constante para el redondeo
    private static final BigDecimal ROUND_MULTIPLE = BigDecimal.valueOf(100);
    private static final int SCALE = 2;

    //Import excel
    @Transactional
    // 🔥 Reemplazar el método importExcel
    public void importExcel(MultipartFile file, List<ImportPriceListDto> priceListsConfig) {

        // 🔥 1. Obtener o crear todas las listas de precios
        Map<Long, PriceList> priceListsMap = new HashMap<>();
        Map<Long, BigDecimal> marginsMap = new HashMap<>();

        for (ImportPriceListDto config : priceListsConfig) {
            // Normalizar el margen (ej: 0.35)
            BigDecimal margin = config.getMargin();

            // Buscar por nombre, si no existe crear
            PriceList priceList = priceListRepository.findByName(config.getName())
                    .orElseGet(() -> {
                        PriceList newList = new PriceList();
                        newList.setName(config.getName());
                        newList.setMargin(margin);
                        newList.setActive(true);
                        return priceListRepository.save(newList);
                    });

            // Si ya existe pero el margen cambió, actualizarlo
            if (priceList.getMargin().compareTo(margin) != 0) {
                priceList.setMargin(margin);
                priceListRepository.save(priceList);
            }

            priceListsMap.put(priceList.getId(), priceList);
            marginsMap.put(priceList.getId(), margin);
        }

        Map<String, Product> productsToSave = new HashMap<>();
        List<Product> productsToUpdate = new ArrayList<>();
        Set<String> importedNames = new HashSet<>();

        // 🔥 2. Guardar los precios calculados temporalmente
        Map<String, Map<Long, BigDecimal>> productPricesToSave = new HashMap<>();
        // Estructura: normalizedName -> (priceListId -> precio)

        try (InputStream is = file.getInputStream();
             XSSFWorkbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0);
            String currentCategory = null;
            List<Product> allProducts = productRepository.findAll();

            Map<String, Product> existingMap = new HashMap<>();
            for (Product p : allProducts) {
                existingMap.put(p.getNormalizedName(), p);
            }

            for (Row row : sheet) {
                if (row.getRowNum() < 10) continue;
                if (row.getCell(1) == null) continue;

                String name = row.getCell(1).toString().trim();
                if (name.isEmpty()) continue;

                String normalizedName = normalize(name);
                String priceRaw = row.getCell(2) != null ? row.getCell(2).toString().trim() : "";
                String normalizedPrice = priceRaw.toLowerCase().replaceAll("\\s+", "");

                importedNames.add(normalizedName);

                // CASO 1: SIN STOCK
                if (normalizedPrice.contains("sinstock")) {
                    Product existing = existingMap.get(normalizedName);
                    if (existing != null) {
                        if (existing.isAvailable()) {
                            existing.setAvailable(false);
                            productsToUpdate.add(existing);
                        }
                    } else {
                        Product product = new Product();
                        product.setName(name);
                        product.setNormalizedName(normalizedName);
                        product.setCategory(currentCategory);
                        product.setAvailable(false);
                        product.setPricePurchase(BigDecimal.ZERO);
                        product.setPriceSale(BigDecimal.ZERO);
                        productsToSave.put(normalizedName, product);
                    }
                    continue;
                }

                // CASO 2: CATEGORÍA
                boolean isCategory = (row.getCell(2) == null || priceRaw.isBlank());
                if (isCategory) {
                    currentCategory = name;
                    continue;
                }

                // CASO 3: PRODUCTO REAL
                BigDecimal pricePurchase;
                try {
                    pricePurchase = parsePrice(priceRaw);
                } catch (Exception e) {
                    System.out.println("⚠️ Error parseando precio: " + priceRaw + " | Producto: " + name);
                    continue;
                }

                Product existing = existingMap.get(normalizedName);

                if (existing != null) {
                    boolean changed = false;

                    // Actualizar precio de compra si cambió
                    if (existing.getPricePurchase().compareTo(pricePurchase) != 0) {
                        existing.setPricePurchase(pricePurchase);
                        changed = true;
                    }

                    // 🔥 SIEMPRE recalcular price_sale (por si era null o el margen cambió)
                    BigDecimal newPriceSale = calculateDefaultPriceSale(pricePurchase);
                    if (existing.getPriceSale() == null || existing.getPriceSale().compareTo(newPriceSale) != 0) {
                        existing.setPriceSale(newPriceSale);
                        changed = true;
                    }

                    String existingCategory = existing.getCategory() == null ? "" : existing.getCategory();
                    if (currentCategory != null && !currentCategory.equalsIgnoreCase(existingCategory)) {
                        existing.setCategory(currentCategory);
                        changed = true;
                    }

                    if (!existing.isAvailable()) {
                        existing.setAvailable(true);
                        changed = true;
                    }
                    if (changed) {
                        productsToUpdate.add(existing);
                    }

                    // 🔥 Calcular precio para cada lista
                    Map<Long, BigDecimal> pricesByList = new HashMap<>();
                    for (Map.Entry<Long, BigDecimal> entry : marginsMap.entrySet()) {
                        BigDecimal margin = entry.getValue();
                        BigDecimal priceSale = pricePurchase.add(pricePurchase.multiply(margin));

                        // 🔥 Si el margen es 0, NO redondear (precio de costo tal cual)
                        BigDecimal finalPrice;
                        if (margin.compareTo(BigDecimal.ZERO) == 0) {
                            finalPrice = priceSale;  // Sin redondeo
                        } else {
                            finalPrice = roundUpTo100(priceSale);  // Con redondeo al múltiplo de 100
                        }

                        pricesByList.put(entry.getKey(), finalPrice);
                    }
                    productPricesToSave.put(normalizedName, pricesByList);

                } else {
                    if (!productsToSave.containsKey(normalizedName)) {
                        Product product = new Product();
                        product.setName(name);
                        product.setNormalizedName(normalizedName);
                        product.setCategory(currentCategory);
                        product.setPricePurchase(pricePurchase);
                        product.setPriceSale(calculateDefaultPriceSale(pricePurchase));
                        product.setAvailable(true);
                        productsToSave.put(normalizedName, product);

                        // 🔥 Calcular precio para cada lista
                        Map<Long, BigDecimal> pricesByList = new HashMap<>();
                        for (Map.Entry<Long, BigDecimal> entry : marginsMap.entrySet()) {
                            BigDecimal margin = entry.getValue();
                            BigDecimal priceSale = pricePurchase.add(pricePurchase.multiply(margin));

                            // 🔥 Si el margen es 0, NO redondear (precio de costo tal cual)
                            BigDecimal finalPrice;
                            if (margin.compareTo(BigDecimal.ZERO) == 0) {
                                finalPrice = priceSale;  // Sin redondeo
                            } else {
                                finalPrice = roundUpTo100(priceSale);  // Con redondeo al múltiplo de 100
                            }

                            pricesByList.put(entry.getKey(), finalPrice);
                        }
                        productPricesToSave.put(normalizedName, pricesByList);
                    }
                }
            }

            // 🔥 3. Guardar productos nuevos
            List<Product> savedProducts = productRepository.saveAll(productsToSave.values());
            productRepository.saveAll(productsToUpdate);

            // 🔥 4. Ahora guardar los precios por lista
            // Recorremos TODOS los productos (nuevos + actualizados + existentes)
            List<Product> allSavedProducts = new ArrayList<>();
            allSavedProducts.addAll(savedProducts);
            allSavedProducts.addAll(productsToUpdate);

            // 🔥 Para los productos que no cambiaron, también hay que recalcular
            // Tomamos todos los productos existentes + nuevos
            Map<String, Product> finalProductsMap = new HashMap<>();
            for (Product p : allProducts) {
                finalProductsMap.put(p.getNormalizedName(), p);
            }
            for (Product p : savedProducts) {
                finalProductsMap.put(p.getNormalizedName(), p);
            }

            for (Map.Entry<String, Map<Long, BigDecimal>> entry : productPricesToSave.entrySet()) {
                String normalizedName = entry.getKey();
                Product product = finalProductsMap.get(normalizedName);
                if (product == null) continue;

                for (Map.Entry<Long, BigDecimal> priceEntry : entry.getValue().entrySet()) {
                    Long priceListId = priceEntry.getKey();
                    BigDecimal priceSale = priceEntry.getValue();
                    PriceList priceList = priceListsMap.get(priceListId);

                    // Buscar si ya existe
                    Optional<ProductPrice> existing = productPriceRepository
                            .findByProductAndPriceList(product, priceList);

                    if (existing.isPresent()) {
                        ProductPrice pp = existing.get();
                        if (pp.getPriceSale().compareTo(priceSale) != 0) {
                            pp.setPriceSale(priceSale);
                            productPriceRepository.save(pp);
                        }
                    } else {
                        ProductPrice pp = new ProductPrice();
                        pp.setProduct(product);
                        pp.setPriceList(priceList);
                        pp.setPriceSale(priceSale);
                        productPriceRepository.save(pp);
                    }
                }
            }

            // 🔥 5. Marcar como no disponibles los que no vinieron
            for (Product product : allProducts) {
                if (!importedNames.contains(product.getNormalizedName())) {
                    product.setAvailable(false);
                }
            }
            productRepository.saveAll(allProducts);

            System.out.println("✅ Nuevos: " + productsToSave.size());
            System.out.println("🔄 Actualizados: " + productsToUpdate.size());
            System.out.println("💰 Precios guardados para " + priceListsMap.size() + " listas");

        } catch (Exception e) {
            throw new RuntimeException("Error al importar Excel", e);
        }
    }

    private String normalize(String text) {
        return text.toLowerCase()
                .trim()
                .replaceAll("\\s+", " ")
                .replaceAll("[^a-z0-9 ]", "");
    }

    private BigDecimal parsePrice(String priceRaw) {
        if (priceRaw == null || priceRaw.isBlank()) {
            throw new RuntimeException("Precio vacío");
        }

        String cleaned = priceRaw.trim();

        // Formato con coma decimal (Argentina)
        if (cleaned.contains(",") && cleaned.contains(".")) {
            cleaned = cleaned.replace(".", "").replace(",", ".");
        } else if (cleaned.contains(",")) {
            cleaned = cleaned.replace(",", ".");
        }

        cleaned = cleaned.replaceAll("[^0-9\\.]", "");

        //  Usar BigDecimal con precisión
        return new BigDecimal(cleaned).setScale(SCALE, RoundingMode.HALF_UP);
    }

    @Transactional(readOnly = true)
    public Optional<Product> findByNormalizedName(String normalizedName){
        return productRepository.findByNormalizedName(normalizedName);
    }

    @Transactional(readOnly = true)
    public List<Product> getAll(){
        return productRepository.findAll();
    }

    //  Método para redondear al múltiplo de 100 superior
    private BigDecimal roundUpTo100(BigDecimal value) {
        if (value == null) return BigDecimal.ZERO;
        return value.divide(ROUND_MULTIPLE, 0, RoundingMode.UP).multiply(ROUND_MULTIPLE);
    }

    // 🔥 Método para obtener el precio de venta redondeado
    public BigDecimal getRoundedPriceSale(Product product) {
        return roundUpTo100(product.getPriceSale());
    }

    //Metodo para exportar el excel
    public ByteArrayInputStream exportProductsToExcel(Long priceListId) {

        // 🔥 Obtener la lista
        PriceList priceList = priceListRepository.findById(priceListId)
                .orElseThrow(() -> new RuntimeException("Lista de precios no encontrada"));

        List<Product> products = productRepository
                .findAllByAvailableTrueOrderByCategoryAscNameAsc();

        try (Workbook workbook = new XSSFWorkbook()) {

            Sheet sheet = workbook.createSheet("Catalogo");

            int rowIdx = 0;

            // 🔥 FUENTES
            Font titleFont = workbook.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 16);

            Font categoryFont = workbook.createFont();
            categoryFont.setBold(true);
            categoryFont.setFontHeightInPoints((short) 12);

            // 🔥 ESTILOS
            CellStyle titleStyle = workbook.createCellStyle();
            titleStyle.setFont(titleFont);

            CellStyle categoryStyle = workbook.createCellStyle();
            categoryStyle.setFont(categoryFont);
            categoryStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            categoryStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            CellStyle priceStyle = workbook.createCellStyle();
            DataFormat format = workbook.createDataFormat();
            priceStyle.setDataFormat(format.getFormat("$#,##0"));

            // 🔥 HEADER
            Row titleRow = sheet.createRow(rowIdx++);
            titleRow.setHeight((short) 1000);
            Cell titleCell = titleRow.createCell(0);
            titleCell.setCellValue("Esperanza Productos Naturales");
            titleCell.setCellStyle(titleStyle);

            Row phoneRow = sheet.createRow(rowIdx++);
            phoneRow.setHeight((short) 500);
            phoneRow.createCell(0).setCellValue("Tel: 3512261066");

            Row dateRow = sheet.createRow(rowIdx++);
            dateRow.setHeight((short) 500);
            dateRow.createCell(0).setCellValue("Fecha: " + java.time.LocalDate.now());

            // 🔥 NUEVO: Lista de precios en el header
            Row listRow = sheet.createRow(rowIdx++);
            listRow.setHeight((short) 500);
            listRow.createCell(0).setCellValue("Lista: " + priceList.getName());

            rowIdx++; // espacio

            // 🔥 LOGO
            try (InputStream is = Thread.currentThread()
                    .getContextClassLoader()
                    .getResourceAsStream("LogoEsperanza.png")) {

                if (is != null) {

                    byte[] bytes = is.readAllBytes();
                    int pictureIdx = workbook.addPicture(bytes, Workbook.PICTURE_TYPE_PNG);

                    CreationHelper helper = workbook.getCreationHelper();
                    Drawing<?> drawing = sheet.createDrawingPatriarch();

                    ClientAnchor anchor = helper.createClientAnchor();
                    anchor.setCol1(3); // columna donde aparece
                    anchor.setRow1(0); // fila donde aparece

                    Picture pict = drawing.createPicture(anchor, pictureIdx);

                    // 🔥 mantiene proporción real
                    pict.resize();

                } else {
                    System.out.println("⚠️ Logo no encontrado");
                }

            } catch (Exception e) {
                e.printStackTrace();
            }

            // 🔥 AGRUPAR POR CATEGORÍA
            Map<String, List<Product>> grouped = products.stream()
                    .collect(Collectors.groupingBy(
                            Product::getCategory,
                            TreeMap::new,
                            Collectors.toList()
                    ));

            for (String category : grouped.keySet()) {

                // 🔥 CATEGORÍA
                Row catRow = sheet.createRow(rowIdx++);
                Cell catCell = catRow.createCell(0);
                catCell.setCellValue(category);
                catCell.setCellStyle(categoryStyle);

                for (Product p : grouped.get(category)) {
                    Row row = sheet.createRow(rowIdx++);

                    // 🔥 Obtener el precio de la lista elegida
                    BigDecimal price = productPriceRepository
                            .findByProductAndPriceList(p, priceList)
                            .map(ProductPrice::getPriceSale)
                            .orElse(BigDecimal.ZERO);

                    row.createCell(0).setCellValue(p.getName());
                    Cell priceCell = row.createCell(1);
                    priceCell.setCellValue(price.doubleValue());
                    priceCell.setCellStyle(priceStyle);
                }

                rowIdx++; // espacio entre categorías
            }

            // 🔥 AJUSTES
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.setColumnWidth(3, 1000); // espacio para logo

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (Exception e) {
            throw new RuntimeException("Error generando Excel", e);
        }
    }

    // 🔥 NUEVO: Obtener productos con el precio de una lista específica
    @Transactional(readOnly = true)
    public List<ProductDto> getProductsByPriceList(Long priceListId) {
        PriceList priceList = priceListRepository.findById(priceListId)
                .orElseThrow(() -> new RuntimeException("Lista de precios no encontrada"));

        List<Product> products = productRepository.findAllByAvailableTrueOrderByCategoryAscNameAsc();

        List<ProductDto> result = new ArrayList<>();
        for (Product product : products) {
            // Buscar el precio para esta lista
            Optional<ProductPrice> pp = productPriceRepository
                    .findByProductAndPriceList(product, priceList);

            // Si no tiene precio en esta lista, lo saltamos
            if (pp.isEmpty()) continue;

            ProductDto dto = new ProductDto();
            dto.setId(product.getId());
            dto.setName(product.getName());
            dto.setNormalizedName(product.getNormalizedName());
            dto.setCategory(product.getCategory());
            dto.setPricePurchase(product.getPricePurchase());
            dto.setPriceSale(pp.get().getPriceSale());  // 🔥 Precio de la lista
            dto.setAvailable(product.isAvailable());
            dto.setPriceListId(priceList.getId());
            dto.setPriceListName(priceList.getName());

            result.add(dto);
        }

        return result;
    }

    /**
     * Calcula el precio de venta base (con el margen por defecto: 35% para consumidor final).
     * Este precio queda como referencia en el producto.
     * La interfaz siempre muestra el precio de la lista seleccionada (product_price).
     */
    private BigDecimal calculateDefaultPriceSale(BigDecimal pricePurchase) {
        BigDecimal defaultMargin = new BigDecimal("0.35"); // 35% por defecto
        BigDecimal priceSale = pricePurchase.add(pricePurchase.multiply(defaultMargin));
        return roundUpTo100(priceSale);
    }
}
