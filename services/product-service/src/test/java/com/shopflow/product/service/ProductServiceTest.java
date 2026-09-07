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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock ProductRepository products;
    @Mock CategoryRepository categories;
    @Mock ProductCache cache;
    @Mock InventoryClient inventoryClient;

    ProductService service() {
        return new ProductService(products, categories, cache, inventoryClient);
    }

    private Category category() {
        return Category.create("Books");
    }

    private CreateProductRequest createRequest(UUID categoryId) {
        return new CreateProductRequest("BOOK-1", "Clean Code", "desc", new BigDecimal("499.00"), categoryId, null);
    }

    @Test
    void createPersistsRegistersInventoryAndReturnsMappedResponse() {
        Category cat = category();
        when(categories.findById(cat.getId())).thenReturn(Optional.of(cat));
        when(products.existsBySkuIgnoreCase("BOOK-1")).thenReturn(false);

        ProductResponse response = service().create(createRequest(cat.getId()));

        verify(products).saveAndFlush(any(Product.class));
        verify(inventoryClient).registerNewProduct(any(UUID.class), eq("BOOK-1"));
        assertThat(response.sku()).isEqualTo("BOOK-1");
        assertThat(response.categoryName()).isEqualTo("Books");
        assertThat(response.active()).isTrue();
    }

    @Test
    void createFailsWhenCategoryDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(categories.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().create(createRequest(missing)))
                .isInstanceOf(ResourceNotFoundException.class);
        verify(products, never()).saveAndFlush(any());
        verifyNoInteractions(inventoryClient);
    }

    @Test
    void createFailsOnDuplicateSku() {
        Category cat = category();
        when(categories.findById(cat.getId())).thenReturn(Optional.of(cat));
        when(products.existsBySkuIgnoreCase("BOOK-1")).thenReturn(true);

        assertThatThrownBy(() -> service().create(createRequest(cat.getId())))
                .isInstanceOf(ConflictException.class);
        verify(products, never()).saveAndFlush(any());
    }

    @Test
    void updateAppliesChangesAndEvictsTheCache() {
        Category cat = category();
        Product product = Product.create("BOOK-1", "Old", "d", new BigDecimal("10.00"), cat, null);
        when(products.findWithCategoryById(product.getId())).thenReturn(Optional.of(product));

        ProductResponse response = service().update(product.getId(),
                new UpdateProductRequest("New Name", null, new BigDecimal("25.00"), null, null, null));

        assertThat(response.name()).isEqualTo("New Name");
        assertThat(response.price()).isEqualByComparingTo("25.00");
        verify(cache).evict(product.getId());
    }

    @Test
    void deactivateSoftDeletesAndEvicts() {
        Category cat = category();
        Product product = Product.create("BOOK-1", "X", "d", new BigDecimal("10.00"), cat, null);
        when(products.findById(product.getId())).thenReturn(Optional.of(product));

        service().deactivate(product.getId());

        assertThat(product.isActive()).isFalse();
        verify(cache).evict(product.getId());
    }

    @Test
    void getActiveByIdReturnsFromCacheWithoutHittingTheDatabaseOnAHit() {
        UUID id = UUID.randomUUID();
        ProductResponse cached = new ProductResponse(id, "S", "N", "d", new BigDecimal("1.00"), "INR",
                UUID.randomUUID(), "Books", "books", null, true, null);
        when(cache.get(id)).thenReturn(Optional.of(cached));

        ProductResponse response = service().getActiveById(id);

        assertThat(response).isEqualTo(cached);
        verify(products, never()).findWithCategoryById(any());
    }

    @Test
    void getActiveByIdLoadsFromDbOnMissAndPopulatesTheCache() {
        Category cat = category();
        Product product = Product.create("BOOK-1", "N", "d", new BigDecimal("10.00"), cat, null);
        when(cache.get(product.getId())).thenReturn(Optional.empty());
        when(products.findWithCategoryById(product.getId())).thenReturn(Optional.of(product));

        ProductResponse response = service().getActiveById(product.getId());

        assertThat(response.sku()).isEqualTo("BOOK-1");
        verify(cache).put(response);
    }

    @Test
    void getActiveByIdHidesInactiveProducts() {
        Category cat = category();
        Product product = Product.create("BOOK-1", "N", "d", new BigDecimal("10.00"), cat, null);
        product.deactivate();
        when(cache.get(product.getId())).thenReturn(Optional.empty());
        when(products.findWithCategoryById(product.getId())).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> service().getActiveById(product.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
