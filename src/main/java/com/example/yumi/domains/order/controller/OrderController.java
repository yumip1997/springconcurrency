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
    public ResponseEntity<Long> createOrder(@Valid @RequestBody OrderRequest request) {
        Long orderNo = orderService.order(request);
        return ResponseEntity.ok(orderNo);
    }
}

