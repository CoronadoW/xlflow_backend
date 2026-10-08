package com.coronado.esflowix.controller;

import com.coronado.esflowix.model.PriceList;
import com.coronado.esflowix.service.PriceListService;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/priceList")
//CorsConfig implements adsCorsMapping for Development (localhost:4200) and Production in the Server (https://xlflow.coronadodev.com)
//@CrossOrigin(origins = "http://localhost:4200")
@RequiredArgsConstructor
@Validated
public class PriceListController {

    private final PriceListService priceListService;

    @GetMapping
    public ResponseEntity<List<PriceList>> getAll(){
        return new ResponseEntity<>(priceListService.getAll(), HttpStatus.OK);
    }

    @GetMapping("/active")
    public ResponseEntity<List<PriceList>> getActive(){
        return new ResponseEntity<>(priceListService.getActive(), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<PriceList> getById(@PathVariable Long id){
        return new ResponseEntity<>(priceListService.getById(id), HttpStatus.OK);
    }

    // 🔥 Crear una lista nueva
    @PostMapping
    public ResponseEntity<PriceList> create(@RequestBody PriceList priceList) {
        return ResponseEntity.ok(priceListService.create(priceList));
    }

    // 🔥 Actualizar una lista
    @PutMapping("/{id}")
    public ResponseEntity<PriceList> update(@PathVariable Long id, @RequestBody PriceList priceList) {
        return ResponseEntity.ok(priceListService.updatePriceList(id, priceList));
    }

    // 🔥 Eliminar una lista
    @DeleteMapping("/{id}")
    public ResponseEntity<String> delete(@PathVariable Long id) {
        priceListService.delete(id);
        return ResponseEntity.ok("Lista eliminada correctamente");
    }

}
