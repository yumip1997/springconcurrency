package com.example.yumi.domains.order.service;

import com.example.yumi.domains.order.dto.StockReduceRequest;
import com.example.yumi.domains.order.entity.Stock;
import com.example.yumi.domains.order.repository.StockRepository;
import com.example.yumi.infra.message.application.sender.AwsSqsMessageSender;
import com.example.yumi.infra.message.dto.SqsMessageEnvelope;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class StockService {

    @Value("${spring.cloud.aws.sqs.stock-queue.name}")
    private String queueName;

    private final AwsSqsMessageSender sqsMessageSender;
    private final StockRepository stockRepository;

    public StockService(AwsSqsMessageSender sqsMessageSender, StockRepository stockRepository) {
        this.sqsMessageSender = sqsMessageSender;
        this.stockRepository = stockRepository;
    }

    @Transactional
    public void sendStockReduceRequest(StockReduceRequest stockReduceRequest){
        SqsMessageEnvelope<StockReduceRequest> sqsMessageEnvelope = stockReduceRequest.toSqsMessageEnvelope(queueName);
        sqsMessageSender.sendMessage(sqsMessageEnvelope);
    }

    @Transactional
    public void reduceStock(StockReduceRequest stockReduceRequest) {
        Stock stock = stockRepository.findByProductNo(stockReduceRequest.getProductNo())
                .orElseThrow(() -> new RuntimeException("상품번호 " + stockReduceRequest.getProductNo() + "에 해당하는 재고를 찾을 수 없습니다"));

        log.info("재고수량 : {}", stock.getStockQuantity());

        stock.reduceStock(stockReduceRequest.getQuantity());

        log.info("차감 후 재고수량 : {}", stock.getStockQuantity());
    }
}

