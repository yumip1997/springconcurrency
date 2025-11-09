package com.example.yumi.domains.order.repository;

import com.example.yumi.domains.order.entity.Stock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface StockRepository extends JpaRepository<Stock, Long> {
    Optional<Stock> findByProductNo(Long productNo);
    
    // 비관적 락 (PESSIMISTIC_WRITE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM Stock s WHERE s.productNo = :productNo")
    Optional<Stock> findByProductNoWithPessimisticLock(@Param("productNo") Long productNo);
    
    // 낙관적 락 (OPTIMISTIC)
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT s FROM Stock s WHERE s.productNo = :productNo")
    Optional<Stock> findByProductNoWithOptimisticLock(@Param("productNo") Long productNo);
}

