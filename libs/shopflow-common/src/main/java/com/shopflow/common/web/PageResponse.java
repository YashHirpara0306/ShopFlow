package com.shopflow.common.web;

import java.util.List;

/**
 * The stable JSON shape for every paginated list endpoint in ShopFlow.
 *
 * <p>Why not just return Spring Data's {@code Page}? Its serialized form is not a stable contract
 * (Spring Boot 3.3+ warns against serializing {@code PageImpl} directly) and it leaks persistence
 * concepts into the API. Services map their {@code Page} to this record in one line.
 *
 * <pre>{@code
 * {
 *   "content": [ ... ],
 *   "page": 0,
 *   "size": 20,
 *   "totalElements": 137,
 *   "totalPages": 7,
 *   "first": true,
 *   "last": false
 * }
 * }</pre>
 *
 * @param page zero-based page index
 */
public record PageResponse<T>(
        List<T> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean first,
        boolean last
) {

    public static <T> PageResponse<T> of(List<T> content, int page, int size, long totalElements) {
        int totalPages = size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size);
        return new PageResponse<>(
                List.copyOf(content),
                page,
                size,
                totalElements,
                totalPages,
                page <= 0,
                page >= totalPages - 1
        );
    }
}
