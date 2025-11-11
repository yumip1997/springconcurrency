package com.example.yumi.domains.order.controller;

import com.example.yumi.domains.order.dto.OrderRequest;
import com.example.yumi.domains.order.service.OrderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(@Valid @RequestBody OrderRequest request) {
        Long orderNo = orderService.order(request);
        Map<String, Object> response = new HashMap<>();
        response.put("orderNo", orderNo);
        response.put("message", "Sqs를 이용한 주문이 완료되었습니다");
        return ResponseEntity.ok(response);
    }

    @PostMapping("/lua")
    public ResponseEntity<Map<String, Object>> createOrderWithLua(@Valid @RequestBody OrderRequest request) {
        Long orderNo = orderService.orderWithLua(request);
        Map<String, Object> response = new HashMap<>();
        response.put("orderNo", orderNo);
        response.put("message", "Lua 스크립트를 이용한 주문이 완료되었습니다");
        return ResponseEntity.ok(response);
    }

}

