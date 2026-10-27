package com.stockflow.repository;

import com.stockflow.domain.Sale;
import com.stockflow.domain.SaleStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    Optional<Sale> findByIdempotencyKey(String idempotencyKey);

    Page<Sale> findByStatus(SaleStatus status, Pageable pageable);

    /** Trava a venda para que dois cancelamentos simultâneos não revertam o estoque duas vezes. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from Sale s where s.id = :id")
    Optional<Sale> findByIdForUpdate(@Param("id") Long id);

    /** Receita por dia (somente vendas concluídas), agrupada no fuso informado. */
    @Query(value = """
            select to_char(s.created_at at time zone :zone, 'YYYY-MM-DD') as bucket,
                   count(*)                                               as sales,
                   coalesce(sum(s.total), 0)                              as revenue
            from sale s
            where s.status = 'COMPLETED'
              and s.created_at >= :fromInstant
              and s.created_at <  :toInstant
            group by 1
            order by 1
            """, nativeQuery = true)
    List<DailyRevenueRow> dailyRevenue(@Param("zone") String zone,
                                       @Param("fromInstant") Instant fromInstant,
                                       @Param("toInstant") Instant toInstant);
}
