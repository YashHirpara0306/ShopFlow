package com.shopflow.product.service;

import com.shopflow.common.error.ConflictException;
import com.shopflow.common.error.ResourceNotFoundException;
import com.shopflow.product.domain.Category;
import com.shopflow.product.repository.CategoryRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {

    private final CategoryRepository categories;

    public CategoryService(CategoryRepository categories) {
        this.categories = categories;
    }

    @Transactional
    public Category create(String name) {
        Category category = Category.create(name);
        if (categories.existsBySlug(category.getSlug())) {
            throw new ConflictException("A category with a similar name already exists");
        }
        try {
            return categories.saveAndFlush(category);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A category with a similar name already exists");
        }
    }

    @Transactional(readOnly = true)
    public List<Category> list() {
        return categories.findAll(Sort.by("name"));
    }

    @Transactional(readOnly = true)
    public Category getById(UUID id) {
        return categories.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Category", id));
    }
}
