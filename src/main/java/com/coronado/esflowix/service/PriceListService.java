package com.coronado.esflowix.service;

import com.coronado.esflowix.model.PriceList;
import com.coronado.esflowix.repository.PriceListRepository;
import jakarta.persistence.EntityExistsException;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PriceListService {

    private final PriceListRepository priceListRepository;

    @Transactional(readOnly = true)
    public List<PriceList> getAll(){
        return priceListRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<PriceList> getActive(){
        return priceListRepository.findAllByActiveTrue();
    }

    @Transactional(readOnly = true)
    public PriceList getById(Long id){
        return priceListRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Lista de precios no encontrada"));
    }

    @Transactional
    public PriceList create(PriceList priceList){
        if(priceListRepository.existsByName(priceList.getName())) {
            throw new EntityExistsException("Lista ya existe con ese nombre");
        }
        return priceListRepository.save(priceList);
    }

    @Transactional
    public PriceList updatePriceList(Long id, PriceList priceList){
        PriceList pl = getById(id);
        pl.setName(priceList.getName());
        pl.setMargin(priceList.getMargin());
        pl.setActive(priceList.isActive());
        return priceListRepository.save(pl);
    }

    @Transactional
    public void delete(Long id){
        priceListRepository.deleteById(id);
    }

    @Transactional
    public PriceList getOrCreate(String name, BigDecimal margin){
        Optional<PriceList> existing = priceListRepository.findByName(name);

        if(existing.isPresent()){
            PriceList list  = existing.get();
            //Si el margen cambió , actualizarlo
            if(list.getMargin().compareTo(margin) !=0) {
                list.setMargin(margin);
                priceListRepository.save(list);
            }
            return list;
        }

        PriceList newList = new PriceList();
        newList.setName(name);
        newList.setMargin(margin);
        newList.setActive(true);
        return priceListRepository.save(newList);
    }
}
