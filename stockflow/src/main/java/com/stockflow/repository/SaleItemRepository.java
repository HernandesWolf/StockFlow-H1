package com.stockflow.repository;

import com.stockflow.domain.SaleItem;
import com.stockflow.domain.SaleStatus;
import com.stockflow.web.dto.TopProductResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    @Query("select new com.stockflow.web.dto.TopProductResponse(p.id, p.sku, p.name, sum(i.quantity), sum(i.lineTotal)) "
            + "from SaleItem i join i.sale s join i.product p "
            + "where s.status = :status and s.createdAt >= :fromInstant and s.createdAt < :toInstant "
            + "group by p.id, p.sku, p.name "
            + "order by sum(i.quantity) desc, sum(i.lineTotal) desc, p.name asc")
    List<TopProductResponse> findTopSelling(@Param("status") SaleStatus status,
                                            @Param("fromInstant") Instant fromInstant,
                                            @Param("toInstant") Instant toInstant,
                                            Pageable pageable);

    /** Custo total das mercadorias vendidas no período (null se não houver vendas). */
    @Query("select sum(i.quantity * i.unitCost) "
            + "from SaleItem i join i.sale s "
            + "where s.status = :status and s.createdAt >= :fromInstant and s.createdAt < :toInstant")
    BigDecimal sumCost(@Param("status") SaleStatus status,
                       @Param("fromInstant") Instant fromInstant,
                       @Param("toInstant") Instant toInstant);
}
