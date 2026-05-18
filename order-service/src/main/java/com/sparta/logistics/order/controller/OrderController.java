package com.sparta.logistics.order.controller;

import com.sparta.logistics.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    @GetMapping("/get-user-info-from-order")
    public String getUserInfo(){
        return orderService.getUserInfo();
    }
}
