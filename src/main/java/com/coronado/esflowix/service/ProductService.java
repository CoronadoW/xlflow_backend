package com.coronado.esflowix.service;

import com.coronado.esflowix.model.Product;

import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
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
public class ProductService {

    private final ProductRepository productRepository;

    // 🔥 Constante para el redondeo
    private static final BigDecimal ROUND_MULTIPLE = BigDecimal.valueOf(100);
    private static final int SCALE = 2;

    public ProductService( ProductRepository productRepository){
        this.productRepository = productRepository;
    }

    //Import excel
    public void importExcel(MultipartFile file, double margin) {
        Map<String, Product> productsToSave = new HashMap<>();
        List<Product> productsToUpdate = new ArrayList<>();
        Set<String> importedNames = new HashSet<>();

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

                // Calcular precio de venta con margen
                BigDecimal marginBD = BigDecimal.valueOf(margin);
                BigDecimal priceSale = pricePurchase.add(pricePurchase.multiply(marginBD));

                // Redondear el precio de venta al múltiplo de 100 superior
                BigDecimal roundedPriceSale = roundUpTo100(priceSale);

                Product existing = existingMap.get(normalizedName);

                if (existing != null) {
                    boolean changed = false;

                    // 🔥 CORREGIDO: Actualizar precio de compra SIEMPRE que cambie
                    if (existing.getPricePurchase().compareTo(pricePurchase) != 0) {
                        existing.setPricePurchase(pricePurchase);
                        changed = true;
                    }

                    // 🔥 NUEVO: Siempre actualizar el precio de venta redondeado
                    // (incluso si el precio de compra no cambió, porque podría estar sin redondear)
                    if (existing.getPriceSale().compareTo(roundedPriceSale) != 0) {
                        existing.setPriceSale(roundedPriceSale);
                        changed = true;
                    }

                    // Actualizar categoría si cambió
                    String existingCategory = existing.getCategory() == null ? "" : existing.getCategory();
                    if (currentCategory != null && !currentCategory.equalsIgnoreCase(existingCategory)) {
                        existing.setCategory(currentCategory);
                        changed = true;
                    }

                    // Actualizar disponibilidad
                    if (!existing.isAvailable()) {
                        existing.setAvailable(true);
                        changed = true;
                    }

                    if (changed) {
                        productsToUpdate.add(existing);
                    }

                } else {
                    if (!productsToSave.containsKey(normalizedName)) {
                        Product product = new Product();
                        product.setName(name);
                        product.setNormalizedName(normalizedName);
                        product.setCategory(currentCategory);
                        product.setPricePurchase(pricePurchase);
                        product.setPriceSale(roundedPriceSale);
                        product.setAvailable(true);
                        productsToSave.put(normalizedName, product);
                    }
                }
            }

            // 🔥 NUEVO: También actualizar productos que no cambiaron de precio pero tienen precio sin redondear
            // Esto es para corregir productos existentes que ya estaban en la base de datos
            for (Product existing : allProducts) {
                // Si el producto está disponible y su precio de venta no es múltiplo de 100
                if (existing.isAvailable()) {
                    BigDecimal currentPrice = existing.getPriceSale();
                    BigDecimal roundedPrice = roundUpTo100(currentPrice);

                    // Si el precio actual no es múltiplo de 100, redondearlo
                    if (currentPrice.compareTo(roundedPrice) != 0) {
                        System.out.println("🔄 Corrigiendo precio de " + existing.getName() +
                                ": " + currentPrice + " → " + roundedPrice);
                        existing.setPriceSale(roundedPrice);
                        productsToUpdate.add(existing);
                    }
                }
            }

            productRepository.saveAll(productsToSave.values());
            productRepository.saveAll(productsToUpdate);

            // Marcar como no disponibles los que no vinieron
            for (Product product : allProducts) {
                if (!importedNames.contains(product.getNormalizedName())) {
                    product.setAvailable(false);
                }
            }
            productRepository.saveAll(allProducts);

            System.out.println("✅ Nuevos: " + productsToSave.size());
            System.out.println("🔄 Actualizados: " + productsToUpdate.size());

        } catch (Exception e) {
            throw new RuntimeException("Error al importar Excel", e);
        }
    }
    /*public void importExcel(MultipartFile file, double margin) {
        Map<String, Product> productsToSave = new HashMap<>();
        List<Product> productsToUpdate = new ArrayList<>();
        Set<String> importedNames = new HashSet<>();

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

                // 🔥 Calcular precio de venta con margen
                BigDecimal marginBD = BigDecimal.valueOf(margin);
                BigDecimal priceSale = pricePurchase.add(pricePurchase.multiply(marginBD));

                // 🔥 Redondear el precio de venta al múltiplo de 100 superior
                BigDecimal roundedPriceSale = roundUpTo100(priceSale);

                Product existing = existingMap.get(normalizedName);

                if (existing != null) {
                    boolean changed = false;

                    // 🔥 Guardar precio de compra sin redondear
                    if (existing.getPricePurchase().compareTo(pricePurchase) != 0) {
                        existing.setPricePurchase(pricePurchase);
                        // 🔥 Guardar precio de venta REDONDEADO
                        existing.setPriceSale(roundedPriceSale);
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

                } else {
                    if (!productsToSave.containsKey(normalizedName)) {
                        Product product = new Product();
                        product.setName(name);
                        product.setNormalizedName(normalizedName);
                        product.setCategory(currentCategory);
                        product.setPricePurchase(pricePurchase);
                        // 🔥 Guardar precio de venta REDONDEADO
                        product.setPriceSale(roundedPriceSale);
                        product.setAvailable(true);
                        productsToSave.put(normalizedName, product);
                    }
                }
            }

            productRepository.saveAll(productsToSave.values());
            productRepository.saveAll(productsToUpdate);

            for (Product product : allProducts) {
                if (!importedNames.contains(product.getNormalizedName())) {
                    product.setAvailable(false);
                }
            }
            productRepository.saveAll(allProducts);

            System.out.println("✅ Nuevos: " + productsToSave.size());
            System.out.println("🔄 Actualizados: " + productsToUpdate.size());

        } catch (Exception e) {
            throw new RuntimeException("Error al importar Excel", e);
        }
    }*/

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

    public Optional<Product> findByNormalizedName(String normalizedName){
        return productRepository.findByNormalizedName(normalizedName);
    }

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
    public ByteArrayInputStream exportProductsToExcel() {

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
                    //  Usar el precio ya redondeado
                    row.createCell(0).setCellValue(p.getName());
                    Cell priceCell = row.createCell(1);
                    priceCell.setCellValue(p.getPriceSale().doubleValue());
                    priceCell.setCellStyle(priceStyle);
                }

                rowIdx++; // espacio entre categorías
            }

            // 🔥 AJUSTES
            sheet.autoSizeColumn(0);
            sheet.autoSizeColumn(1);
            sheet.setColumnWidth(3, 5000); // espacio para logo

            ByteArrayOutputStream out = new ByteArrayOutputStream();
            workbook.write(out);
            return new ByteArrayInputStream(out.toByteArray());

        } catch (Exception e) {
            throw new RuntimeException("Error generando Excel", e);
        }
    }





    //Metodo para mostrar los productos que subieron
    //Metodo para mostrar los productos que bajaron
    //Metodo para mostrar los productos que no estan disponibles en la lista


}
