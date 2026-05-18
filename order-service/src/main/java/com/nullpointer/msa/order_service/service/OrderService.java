package com.nullpointer.msa.order_service.service;

import com.nullpointer.msa.order_service.client.UserServiceClient;
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
