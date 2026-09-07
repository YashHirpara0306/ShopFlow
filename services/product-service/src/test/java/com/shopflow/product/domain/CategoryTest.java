package com.shopflow.product.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryTest {

    @Test
    void slugIsLowercasedAndPunctuationCollapsedToHyphens() {
        assertThat(Category.toSlug("  Home & Kitchen! ")).isEqualTo("home-kitchen");
        assertThat(Category.toSlug("Books")).isEqualTo("books");
        assertThat(Category.toSlug("!!!")).isEqualTo("category");
    }

    @Test
    void createDerivesSlugFromName() {
        Category c = Category.create("Sports Equipment");
        assertThat(c.getName()).isEqualTo("Sports Equipment");
        assertThat(c.getSlug()).isEqualTo("sports-equipment");
        assertThat(c.getId()).isNotNull();
    }
}
