package com.example.yumi.repository;

import com.example.yumi.entity.Inventory;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    Optional<Inventory> findByProductNo(Long productNo);
    
    // 비관적 락 (PESSIMISTIC_WRITE)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Inventory i WHERE i.productNo = :productNo")
    Optional<Inventory> findByProductNoWithPessimisticLock(@Param("productNo") Long productNo);
    
    // 낙관적 락 (OPTIMISTIC)
    @Lock(LockModeType.OPTIMISTIC)
    @Query("SELECT i FROM Inventory i WHERE i.productNo = :productNo")
    Optional<Inventory> findByProductNoWithOptimisticLock(@Param("productNo") Long productNo);
}

