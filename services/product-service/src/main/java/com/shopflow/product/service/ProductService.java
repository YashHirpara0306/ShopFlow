package com.shopflow.product.service;

import com.shopflow.common.error.ConflictException;
import com.shopflow.common.error.ResourceNotFoundException;
import com.shopflow.product.api.dto.CreateProductRequest;
import com.shopflow.product.api.dto.ProductResponse;
import com.shopflow.product.api.dto.UpdateProductRequest;
import com.shopflow.product.client.InventoryClient;
import com.shopflow.product.domain.Category;
import com.shopflow.product.domain.Product;
import com.shopflow.product.repository.CategoryRepository;
import com.shopflow.product.repository.ProductRepository;
import com.shopflow.product.repository.ProductSpecifications;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository products;
    private final CategoryRepository categories;
    private final ProductCache cache;
    private final InventoryClient inventoryClient;

    public ProductService(ProductRepository products,
                          CategoryRepository categories,
                          ProductCache cache,
                          InventoryClient inventoryClient) {
        this.products = products;
        this.categories = categories;
        this.cache = cache;
        this.inventoryClient = inventoryClient;
    }

    @Transactional
    public ProductResponse create(CreateProductRequest request, String bearerToken) {
        Category category = categories.findById(request.categoryId())
                .orElseThrow(() -> ResourceNotFoundException.of("Category", request.categoryId()));
        if (products.existsBySkuIgnoreCase(request.sku().trim())) {
            throw new ConflictException("A product with SKU '" + request.sku().trim() + "' already exists");
        }

        Product product = Product.create(request.sku(), request.name(), request.description(),
                request.price(), category, request.imageUrl());
        try {
            products.saveAndFlush(product);
        } catch (DataIntegrityViolationException race) {
            throw new ConflictException("A product with SKU '" + request.sku().trim() + "' already exists");
        }

        // ADR-0005: give Inventory a stock row (at 0) via REST, not an event.
        inventoryClient.registerNewProduct(product.getId(), product.getSku(), bearerToken);
        log.info("product created id={} sku={}", product.getId(), product.getSku());
        return ProductMapper.toResponse(product);
    }

    @Transactional
    public ProductResponse update(UUID id, UpdateProductRequest request) {
        Product product = products.findWithCategoryById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", id));

        Category category = null;
        if (request.categoryId() != null) {
            category = categories.findById(request.categoryId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Category", request.categoryId()));
        }
        product.update(request.name(), request.description(), request.price(), category, request.imageUrl());
        if (request.active() != null) {
            if (request.active()) {
                product.activate();
            } else {
                product.deactivate();
            }
        }

        cache.evict(id);
        return ProductMapper.toResponse(product);
    }

    @Transactional
    public void deactivate(UUID id) {
        Product product = products.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", id));
        product.deactivate();
        cache.evict(id);
        log.info("product deactivated id={}", id);
    }

    /** Public read: cache-aside, active products only. */
    @Transactional(readOnly = true)
    public ProductResponse getActiveById(UUID id) {
        ProductResponse cached = cache.get(id).orElse(null);
        if (cached != null) {
            return cached;
        }
        Product product = products.findWithCategoryById(id)
                .filter(Product::isActive)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", id));
        ProductResponse response = ProductMapper.toResponse(product);
        cache.put(response);
        return response;
    }

    /** Admin read: any product, no cache. */
    @Transactional(readOnly = true)
    public ProductResponse getAnyById(UUID id) {
        return products.findWithCategoryById(id)
                .map(ProductMapper::toResponse)
                .orElseThrow(() -> ResourceNotFoundException.of("Product", id));
    }

    @Transactional(readOnly = true)
    public Page<ProductResponse> listActive(ProductQuery query, Pageable pageable) {
        Specification<Product> spec = ProductSpecifications.activeOnly()
                .and(ProductSpecifications.categorySlug(query.categorySlug()))
                .and(ProductSpecifications.priceAtLeast(query.minPrice()))
                .and(ProductSpecifications.priceAtMost(query.maxPrice()))
                .and(ProductSpecifications.nameContains(query.search()));
        return products.findAll(spec, pageable).map(ProductMapper::toResponse);
    }
}
