package com.shopflow.common.web;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    void computesTotalPagesAndBoundaryFlags() {
        PageResponse<String> first = PageResponse.of(List.of("a", "b"), 0, 2, 5);
        assertThat(first.totalPages()).isEqualTo(3);
        assertThat(first.first()).isTrue();
        assertThat(first.last()).isFalse();

        PageResponse<String> last = PageResponse.of(List.of("e"), 2, 2, 5);
        assertThat(last.first()).isFalse();
        assertThat(last.last()).isTrue();
    }

    @Test
    void handlesEmptyResult() {
        PageResponse<String> empty = PageResponse.of(List.of(), 0, 20, 0);
        assertThat(empty.totalPages()).isZero();
        assertThat(empty.content()).isEmpty();
        assertThat(empty.first()).isTrue();
    }

    @Test
    void contentIsCopiedDefensively() {
        var mutable = new java.util.ArrayList<>(List.of("a"));
        PageResponse<String> pr = PageResponse.of(mutable, 0, 10, 1);
        mutable.add("b");
        assertThat(pr.content()).containsExactly("a");
    }
}
