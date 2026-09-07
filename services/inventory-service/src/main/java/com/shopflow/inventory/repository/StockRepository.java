package com.shopflow.inventory.repository;

import com.shopflow.inventory.domain.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface StockRepository extends JpaRepository<Stock, UUID> {

    /**
     * Atomic, oversell-safe reservation of one product. Returns 1 if the row existed and had enough
     * available (now moved to reserved), 0 otherwise. Concurrent callers serialize on the row lock
     * and re-check {@code available >= :quantity}, so at most the true stock can be reserved.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Stock s
               set s.available = s.available - :quantity,
                   s.reserved  = s.reserved  + :quantity
             where s.productId = :productId
               and s.available >= :quantity
            """)
    int tryReserve(@Param("productId") UUID productId, @Param("quantity") long quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Stock s
               set s.available = s.available + :quantity,
                   s.reserved  = s.reserved  - :quantity
             where s.productId = :productId
               and s.reserved >= :quantity
            """)
    int release(@Param("productId") UUID productId, @Param("quantity") long quantity);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Stock s
               set s.total    = s.total    - :quantity,
                   s.reserved = s.reserved - :quantity
             where s.productId = :productId
               and s.reserved >= :quantity
            """)
    int confirm(@Param("productId") UUID productId, @Param("quantity") long quantity);
}
