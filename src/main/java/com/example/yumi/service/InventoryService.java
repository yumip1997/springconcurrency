package com.example.yumi.service;

import com.example.yumi.entity.Inventory;
import com.example.yumi.repository.InventoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final InventoryRepository inventoryRepository;

    @Transactional
    public void reduceStock(Long productNo, Integer quantity) {
        // 비관적 락을 사용하여 조회
        Inventory inventory = inventoryRepository.findByProductNoWithPessimisticLock(productNo)
                .orElseThrow(() -> new RuntimeException("상품번호 " + productNo + "에 해당하는 재고를 찾을 수 없습니다"));

        log.info("재고수량 : {}", inventory.getStockQuantity());

        inventory.reduceStock(quantity);
    }
}

