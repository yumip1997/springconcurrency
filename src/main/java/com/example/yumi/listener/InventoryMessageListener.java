package com.example.yumi.listener;

import com.example.yumi.dto.InventoryReduceRequest;
import com.example.yumi.service.InventoryService;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.annotation.SqsListener;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryMessageListener {

    private final InventoryService inventoryService;
    private final ObjectMapper objectMapper;

    @SqsListener("yumi-stock")
    public void receiveInventoryReduceMessage(String message) {
        try {
            log.info("SQS 메시지 수신: {}", message);
            
            InventoryReduceRequest request = objectMapper.readValue(message, InventoryReduceRequest.class);
            log.info("재고 차감 요청 - 상품번호: {}, 차감수량: {}", request.getProductNo(), request.getQuantity());

            inventoryService.reduceStock(request.getProductNo(), request.getQuantity());
            
            log.info("재고 차감 완료 - 상품번호: {}, 차감수량: {}", request.getProductNo(), request.getQuantity());
        } catch (Exception e) {
            log.error("재고 차감 처리 중 오류 발생: {}", e.getMessage(), e);
            throw new RuntimeException("재고 차감 처리 실패: " + e.getMessage(), e);
        }
    }
}

