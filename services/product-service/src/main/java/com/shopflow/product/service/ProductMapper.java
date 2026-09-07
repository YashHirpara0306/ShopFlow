package com.shopflow.product.service;

import com.shopflow.product.api.dto.CategoryResponse;
import com.shopflow.product.api.dto.ProductResponse;
import com.shopflow.product.domain.Category;
import com.shopflow.product.domain.Product;

public final class ProductMapper {

    private ProductMapper() {
    }

    public static ProductResponse toResponse(Product p) {
        Category c = p.getCategory();
        return new ProductResponse(
                p.getId(), p.getSku(), p.getName(), p.getDescription(),
                p.getPrice(), p.getCurrency(),
                c.getId(), c.getName(), c.getSlug(),
                p.getImageUrl(), p.isActive(), p.getCreatedAt());
    }

    public static CategoryResponse toResponse(Category c) {
        return new CategoryResponse(c.getId(), c.getName(), c.getSlug());
    }
}
