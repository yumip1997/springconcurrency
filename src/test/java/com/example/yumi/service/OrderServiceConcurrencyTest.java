package com.example.yumi.service;

import com.example.yumi.domains.order.dto.OrderRequest;
import com.example.yumi.domains.order.entity.Stock;
import com.example.yumi.domains.order.repository.StockRepository;
import com.example.yumi.domains.order.service.OrderService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Slf4j
class OrderServiceConcurrencyTest {

    @Autowired
    private StockRepository stockRepository;
    @Autowired
    private OrderService orderService;

    /**
     * FIFO SQS + MessageGroupId를 통한 동시성 제어 테스트
     *
     * 테스트 시나리오:
     * 1. 초기 재고: 100개
     * 2. 동시에 10개의 쓰레드가 각각 1개씩 주문 (총 10개 주문)
     * 3. FIFO SQS의 MessageGroupId (STOCK-{productNo})로 인해 같은 상품의 재고 차감 메시지는 순차적으로 처리됨
     * 4. 최종 재고: 90개 (100 - 10)
     *
     * 검증:
     * - SQS 메시지가 순차적으로 처리되어 동시성 이슈 없이 재고가 정확히 차감되는지 확인
     * - FIFO 큐 특성상 같은 MessageGroupId의 메시지는 순서대로 처리됨
     */
    @Test
    void fifoSqsConcurrencyTest() throws InterruptedException {
        // Given: 초기 재고 100개 설정
        stockRepository.deleteAll();
        stockRepository.saveAndFlush(new Stock(1L, 100));

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch countDownLatch = new CountDownLatch(threadCount);

        // When: 10개의 쓰레드에서 동시에 주문 요청
        for (int i = 0; i < threadCount; i++) {
            int finalI = i;
            executorService.execute(() -> {
                try {
                    OrderRequest request = OrderRequest.builder()
                            .memberNo((long) (finalI + 1))
                            .productNo(1L)  // 모두 같은 상품 주문
                            .orderQuantity(1)
                            .build();

                    Long orderNo = orderService.order(request);
                    log.info("주문 완료: orderNo={}, memberNo={}", orderNo, finalI + 1);
                } catch (Exception e) {
                    log.error("주문 실패: memberNo={}", finalI + 1, e);
                } finally {
                    countDownLatch.countDown();
                }
            });
        }

        countDownLatch.await();
        executorService.shutdown();

        // SQS 메시지 처리 대기 (FIFO 큐에서 순차 처리 시간 고려)
        log.info("SQS 메시지 처리 대기 중...");
        Thread.sleep(15000); // 15초 대기

        // Then: 재고가 정확히 90개인지 확인
        Stock stock = stockRepository.findByProductNo(1L)
                .orElseThrow(() -> new RuntimeException("재고를 찾을 수 없습니다"));

        log.info("=== 테스트 결과 ===");
        log.info("초기 재고: 100개");
        log.info("주문 수량: {}개", threadCount);
        log.info("최종 재고: {}개", stock.getStockQuantity());
        log.info("예상 재고: {}개", 100 - threadCount);

        assertThat(stock.getStockQuantity()).isEqualTo(100 - threadCount);
    }

}

