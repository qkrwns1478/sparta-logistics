package com.sparta.logistics.order.service;

import com.sparta.logistics.order.client.UserServiceClient;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final UserServiceClient userServiceClient;

    public String getUserInfo() {
        return userServiceClient.getUserData();
    }
}
