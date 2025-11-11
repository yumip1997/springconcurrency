package com.example.yumi.domains.order.service;

import com.example.yumi.common.redis.application.LuaExecutor;
import com.example.yumi.common.redis.dto.LuaType;
import com.example.yumi.domains.order.dto.StockReduceRequest;
import com.example.yumi.domains.order.entity.Stock;
import com.example.yumi.domains.order.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class StockServiceWithLua {

    private final LuaExecutor luaExecutor;
    private final StockRepository stockRepository;

    public void reduceStock(StockReduceRequest stockReduceRequest) {
        String key = "stock:" + stockReduceRequest.getProductNo();
        
        // Lazy loading을 포함한 Lua 스크립트 실행
        Long result = luaExecutor.executeWithLazyLoad(
            LuaType.DECREASE, 
            key, 
            () -> {
                // Supplier: Redis에 key가 없을 때만 실행됨
                Stock stock = stockRepository.findByProductNo(stockReduceRequest.getProductNo())
                        .orElseThrow(() -> new RuntimeException("상품번호 " + stockReduceRequest.getProductNo() + "에 해당하는 재고를 찾을 수 없습니다"));
                return stock.getStockQuantity();
            },
            stockReduceRequest.getQuantity()
        );

        if (result == null || result < 0) {
            throw new RuntimeException("재고수량이 부족합니다. 상품번호: " + stockReduceRequest.getProductNo());
        }

        log.info("재고 차감 완료. 주문번호: {} 상품번호: {}, 차감수량: {}, 남은재고: {}",
                stockReduceRequest.getOrderId(),
                stockReduceRequest.getProductNo(), 
                stockReduceRequest.getQuantity(), 
                result);
    }
}

