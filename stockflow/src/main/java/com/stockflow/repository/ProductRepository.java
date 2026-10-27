package com.stockflow.repository;

import com.stockflow.domain.Product;
import com.stockflow.web.dto.StockValuationResponse;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    boolean existsBySku(String sku);

    boolean existsByCategoryId(Long categoryId);

    boolean existsBySupplierId(Long supplierId);

    /**
     * Busca UM produto travando a linha (SELECT ... FOR UPDATE).
     * Quem chegar depois fica esperando até a transação atual terminar.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(@Param("id") Long id);

    /**
     * Busca VÁRIOS produtos travando as linhas, SEMPRE em ordem crescente de id.
     * A ordem fixa evita deadlock: se duas vendas envolvem os produtos A e B,
     * ambas tentam travar A primeiro e depois B (nunca uma em A->B e outra em B->A).
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id in :ids order by p.id")
    List<Product> findAllByIdForUpdate(@Param("ids") Collection<Long> ids);

    /** Produtos ativos com estoque igual ou abaixo do mínimo, os mais críticos primeiro. */
    @Query("select p from Product p where p.active = true and p.stockQuantity <= p.minStock "
            + "order by p.stockQuantity asc, p.name asc")
    List<Product> findReorderAlerts();

    /** Produtos ativos ordenados pela margem bruta percentual (maior primeiro). */
    @Query("select p from Product p where p.active = true and p.salePrice > 0 "
            + "order by (p.salePrice - p.costPrice) / p.salePrice desc, p.name asc")
    List<Product> findTopMargin(Pageable pageable);

    @Query("select new com.stockflow.web.dto.StockValuationResponse("
            + "sum(p.stockQuantity), sum(p.stockQuantity * p.costPrice), sum(p.stockQuantity * p.salePrice)) "
            + "from Product p where p.active = true")
    StockValuationResponse stockValuation();
}
