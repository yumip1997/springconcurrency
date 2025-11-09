package com.example.yumi.infra.message.application.listener;

import com.example.yumi.domains.order.dto.StockReduceRequest;
import com.example.yumi.domains.order.service.StockService;
import io.awspring.cloud.sqs.annotation.SqsListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class StockMessageListener {

    private final StockService stockService;

    @SqsListener(queueNames = "${spring.cloud.aws.sqs.stock-queue.name}")
    public void listenOrderQueue(@Headers Map<String, Object> headers, StockReduceRequest stockReduceRequest) {
        log.info("Received SQS message: productNo={}, quantity={}",
                stockReduceRequest.getProductNo(), stockReduceRequest.getQuantity());
        stockService.reduceStock(stockReduceRequest);
    }
}


