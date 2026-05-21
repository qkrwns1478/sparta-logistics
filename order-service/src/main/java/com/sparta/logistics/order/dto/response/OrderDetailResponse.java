package com.sparta.logistics.order.dto.response;

import com.sparta.logistics.order.domain.Order;
import com.sparta.logistics.order.domain.OrderStatus;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
public class OrderDetailResponse {

    private final UUID orderId;
    private final UUID requesterCompanyId;
    private final UUID receiverCompanyId;
    private final UUID requesterUserId;
    private final OrderStatus status;
    private final Long totalAmount;
    private final LocalDateTime dueDate;
    private final String requestMemo;
    private final List<OrderItemResponse> orderItems;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    private OrderDetailResponse(Order order) {
        this.orderId = order.getId();
        this.requesterCompanyId = order.getRequesterCompanyId();
        this.receiverCompanyId = order.getReceiverCompanyId();
        this.requesterUserId = order.getRequesterUserId();
        this.status = order.getStatus();
        this.totalAmount = order.getTotalAmount();
        this.dueDate = order.getDueDate();
        this.requestMemo = order.getRequestMemo();
        this.orderItems = order.getOrderItems().stream()
                .map(OrderItemResponse::from)
                .toList();
        this.createdAt = order.getCreatedAt();
        this.updatedAt = order.getUpdatedAt();
    }

    public static OrderDetailResponse from(Order order) {
        return new OrderDetailResponse(order);
    }
}