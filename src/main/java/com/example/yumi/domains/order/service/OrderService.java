package com.example.yumi.domains.order.service;

import com.example.yumi.domains.order.dto.OrderRequest;
import com.example.yumi.domains.order.entity.Order;
import com.example.yumi.domains.order.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final StockService stockService;

    @Transactional
    public Long order(OrderRequest request) {
        // 1. 주문 entity 저장
        Order savedOrder = orderRepository.save(request.toOrder());

        // 2. 재고 차감 요청
        stockService.sendStockReduceRequest(request.toStockReduceRequest(savedOrder.getOrderNo()));

        return savedOrder.getOrderNo();
    }
}

