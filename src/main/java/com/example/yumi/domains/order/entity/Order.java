package com.example.yumi.domains.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderNo;

    @Column(nullable = false)
    private Long memberNo;

    @Column(nullable = false)
    private Long productNo;

    @Column(nullable = false)
    private Integer orderQuantity;

    public Order(Long memberNo, Long productNo, Integer orderQuantity) {
        this.memberNo = memberNo;
        this.productNo = productNo;
        this.orderQuantity = orderQuantity;
    }
}

