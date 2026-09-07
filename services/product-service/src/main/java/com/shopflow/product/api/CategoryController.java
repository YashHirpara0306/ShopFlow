package com.shopflow.product.api;

import com.shopflow.product.api.dto.CategoryRequest;
import com.shopflow.product.api.dto.CategoryResponse;
import com.shopflow.product.service.CategoryService;
import com.shopflow.product.service.ProductMapper;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@Tag(name = "Categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    @Operation(summary = "List all categories (public)")
    public List<CategoryResponse> list() {
        return categoryService.list().stream().map(ProductMapper::toResponse).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a category (admin)")
    public CategoryResponse create(@Valid @RequestBody CategoryRequest request) {
        return ProductMapper.toResponse(categoryService.create(request.name()));
    }
}
