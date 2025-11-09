package com.example.yumi.domains.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Stock {

    @Id
    @Column(nullable = false, unique = true)
    private Long productNo;

    @Column(nullable = false)
    private Integer stockQuantity;

    public Stock(Long productNo, Integer stockQuantity) {
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

