package com.example.yumi.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "inventory")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Inventory {

    @Id
    @Column(nullable = false, unique = true)
    private Long productNo;

    @Column(nullable = false)
    private Integer stockQuantity;

    // 낙관적 락을 위한 버전 필드
    @Version
    private Long version;

    public Inventory(Long productNo, Integer stockQuantity) {
        this.productNo = productNo;
        this.stockQuantity = stockQuantity;
    }

    public void reduceStock(Integer quantity) {
        if (this.stockQuantity - quantity < 0) {
            throw new RuntimeException("재고수량이 부족합니다");
        }

        this.stockQuantity -= quantity;
    }
}

