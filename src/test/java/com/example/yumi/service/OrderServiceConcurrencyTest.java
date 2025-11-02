package com.example.yumi.service;

import com.example.yumi.dto.OrderRequest;
import com.example.yumi.entity.Inventory;
import com.example.yumi.repository.InventoryRepository;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Slf4j
class OrderServiceConcurrencyTest {

    @Autowired
    private InventoryRepository inventoryRepository;
    @Autowired
    private OrderService orderService;

    @BeforeEach
    void setUp() {
        inventoryRepository.saveAndFlush(new Inventory(1L, 10));
    }

    @Test
    void concurrencyTest() throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(3);

        int count = 10;
        CountDownLatch countDownLatch = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            int finalI = i;
            executorService.execute(() -> {
                try {
                    OrderRequest request = OrderRequest.builder()
                            .memberNo((long) (finalI + 1))
                            .productNo(1L)
                            .orderQuantity(1)
                            .build();

                    orderService.order(request);

                    Thread.sleep(2000);
                }catch (Exception e){
                    log.error("주문 시 에러 발생", e);
                }finally {
                    countDownLatch.countDown();
                }
            });
        }

        countDownLatch.await();

        inventoryRepository.findByProductNo(1L).ifPresent(inventory -> {
            assertThat(inventory.getStockQuantity()).isEqualTo(0);
        });
    }

}

