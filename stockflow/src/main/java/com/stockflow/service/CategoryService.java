package com.stockflow.service;

import com.stockflow.domain.Category;
import com.stockflow.exception.ConflictException;
import com.stockflow.exception.NotFoundException;
import com.stockflow.repository.CategoryRepository;
import com.stockflow.repository.ProductRepository;
import com.stockflow.web.dto.CategoryRequest;
import com.stockflow.web.dto.CategoryResponse;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Transactional(readOnly = true)
public class CategoryService {

    private final CategoryRepository categories;
    private final ProductRepository products;

    public CategoryService(CategoryRepository categories, ProductRepository products) {
        this.categories = categories;
        this.products = products;
    }

    public List<CategoryResponse> list() {
        return categories.findAll(Sort.by("name")).stream().map(CategoryResponse::from).toList();
    }

    public CategoryResponse get(Long id) {
        return CategoryResponse.from(find(id));
    }

    @Transactional
    public CategoryResponse create(CategoryRequest request) {
        String name = request.name().trim();
        if (categories.existsByName(name)) {
            throw new ConflictException("Já existe uma categoria chamada '" + name + "'.");
        }
        return CategoryResponse.from(categories.save(new Category(name)));
    }

    @Transactional
    public CategoryResponse update(Long id, CategoryRequest request) {
        Category category = find(id);
        String name = request.name().trim();
        if (!category.getName().equals(name) && categories.existsByName(name)) {
            throw new ConflictException("Já existe uma categoria chamada '" + name + "'.");
        }
        category.setName(name);
        return CategoryResponse.from(category);
    }

    @Transactional
    public void delete(Long id) {
        Category category = find(id);
        if (products.existsByCategoryId(id)) {
            throw new ConflictException("A categoria está em uso por produtos e não pode ser removida.");
        }
        categories.delete(category);
    }

    private Category find(Long id) {
        return categories.findById(id).orElseThrow(() -> new NotFoundException("Categoria", id));
    }
}
