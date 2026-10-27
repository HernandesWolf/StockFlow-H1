package com.stockflow.service;

import com.stockflow.domain.Supplier;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.NotFoundException;
import com.stockflow.repository.ProductRepository;
import com.stockflow.repository.SupplierRepository;
import com.stockflow.web.dto.SupplierRequest;
import com.stockflow.web.dto.SupplierResponse;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class SupplierService {

    private final SupplierRepository suppliers;
    private final ProductRepository products;

    public SupplierService(SupplierRepository suppliers, ProductRepository products) {
        this.suppliers = suppliers;
        this.products = products;
    }

    public List<SupplierResponse> list() {
        return suppliers.findAll(Sort.by("name")).stream().map(SupplierResponse::from).toList();
    }

    public SupplierResponse get(Long id) {
        return SupplierResponse.from(find(id));
    }

    @Transactional
    public SupplierResponse create(SupplierRequest request) {
        Supplier supplier = new Supplier(request.name().trim(), request.email(), request.phone());
        return SupplierResponse.from(suppliers.save(supplier));
    }

    @Transactional
    public SupplierResponse update(Long id, SupplierRequest request) {
        Supplier supplier = find(id);
        supplier.update(request.name().trim(), request.email(), request.phone());
        return SupplierResponse.from(supplier);
    }

    @Transactional
    public void delete(Long id) {
        Supplier supplier = find(id);
        if (products.existsBySupplierId(id)) {
            throw new ConflictException("O fornecedor está vinculado a produtos e não pode ser removido.");
        }
        suppliers.delete(supplier);
    }

    private Supplier find(Long id) {
        return suppliers.findById(id).orElseThrow(() -> new NotFoundException("Fornecedor", id));
    }
}
