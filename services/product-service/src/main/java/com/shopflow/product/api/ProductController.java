package com.shopflow.product.api;

import com.shopflow.common.web.PageResponse;
import com.shopflow.product.api.dto.CreateProductRequest;
import com.shopflow.product.api.dto.ProductResponse;
import com.shopflow.product.api.dto.UpdateProductRequest;
import com.shopflow.product.service.ProductQuery;
import com.shopflow.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.UUID;

@RestController
@RequestMapping("/api/products")
@Tag(name = "Products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    @Operation(summary = "List active products with pagination, sorting and filtering (public)")
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(name = "q", required = false) String search,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<ProductResponse> page = productService.listActive(
                new ProductQuery(category, minPrice, maxPrice, search), pageable);
        return PageResponse.of(page.getContent(), page.getNumber(), page.getSize(), page.getTotalElements());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get an active product by id (public, cache-aside)")
    public ProductResponse get(@PathVariable UUID id) {
        return productService.getActiveById(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Create a product (admin)")
    public ProductResponse create(@Valid @RequestBody CreateProductRequest request,
                                  @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return productService.create(request, authorization);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Update a product (admin); invalidates the cache")
    public ProductResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Deactivate a product (admin, soft delete)")
    public void deactivate(@PathVariable UUID id) {
        productService.deactivate(id);
    }
}
