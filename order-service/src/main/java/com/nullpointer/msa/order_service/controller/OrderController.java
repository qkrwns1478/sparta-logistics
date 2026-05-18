package com.nullpointer.msa.order_service.controller;

import com.nullpointer.msa.order_service.client.UserServiceClient;
import com.nullpointer.msa.order_service.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
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
