package com.example.yumi.service;

import com.example.yumi.dto.InventoryReduceRequest;
import com.example.yumi.dto.OrderRequest;
import com.example.yumi.entity.Order;
import com.example.yumi.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.awspring.cloud.sqs.operations.SendResult;
import io.awspring.cloud.sqs.operations.SqsTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {
    private final OrderRepository orderRepository;
    private final SqsTemplate sqsTemplate;

    @Value("${spring.cloud.aws.sqs.queue.name}")
    private String inventoryReduceQueue;

    @Transactional
    public Long order(OrderRequest request) {
        // 1. 주문 entity 저장
        Order savedOrder = orderRepository.save(request.toOrder());

        // 2. 재고 차감
        reduceInventory(request.toInventoryReduceRequest());

        return savedOrder.getOrderNo();
    }

    private void reduceInventory(InventoryReduceRequest inventoryReduceRequest) {
        ObjectMapper objectMapper = new ObjectMapper();

        try{
            String message = objectMapper.writeValueAsString(inventoryReduceRequest);

            SendResult<Object> result = sqsTemplate.send(to -> to
                    .queue(inventoryReduceQueue)
                    .payload(message)
            );

            log.info("message send success : {} ", result.messageId().toString());
        }catch (Exception e){
            log.error("send inventory reduce request error", e);
        }

    }
}

