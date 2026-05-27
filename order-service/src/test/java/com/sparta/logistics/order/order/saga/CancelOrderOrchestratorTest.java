package com.sparta.logistics.order.order.saga;

import com.sparta.logistics.order.kafka.producer.OrderEventPublisher;
import com.sparta.logistics.order.order.entity.Order;
import com.sparta.logistics.order.order.enums.OrderStatus;
import com.sparta.logistics.order.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.order.exception.OrderErrorCode;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CancelOrderOrchestratorTest {

    @InjectMocks
    private CancelOrderOrchestrator orchestrator;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    private final UUID ORDER_ID = UUID.randomUUID();
    private final UUID USER_ID = UUID.randomUUID();
    private final UUID REQUESTER_COMPANY_ID = UUID.randomUUID();
    private final UUID RECEIVER_COMPANY_ID = UUID.randomUUID();
    private final LocalDateTime DUE_DATE = LocalDateTime.now().plusDays(7);

    // ===== start =====

    // start() 호출 시 CANCELLING으로 전이되고 cancel.delivery.command가 발행되는지 검증
    @Test
    void start_transitionsToCancellingAndPublishesCommand() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        ReflectionTestUtils.setField(order, "id", ORDER_ID); // Kafka 파티션 키 생성에 필요

        orchestrator.start(order, USER_ID, "단순 변심");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLING);
        assertThat(order.getCancelledBy()).isEqualTo(USER_ID);
        assertThat(order.getCancelReason()).isEqualTo("단순 변심");
        verify(orderRepository).save(order);
        verify(orderEventPublisher).publishCancelDeliveryCommand(eq(ORDER_ID), any());
    }

    // 이미 CANCELLED 상태의 주문에 start() 호출 시 ORDER_NOT_CANCELLABLE 예외가 발생하는지 검증
    @Test
    void start_alreadyCancelled_throwsException() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        order.cancel(USER_ID, "이미 취소됨");
        ReflectionTestUtils.setField(order, "id", ORDER_ID);

        assertThatThrownBy(() -> orchestrator.start(order, USER_ID, "재취소 시도"))
                .isInstanceOf(BusinessException.class)
                .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                        .isEqualTo(OrderErrorCode.ORDER_NOT_CANCELLABLE));
    }

    // ===== onDeliveryCancelled =====

    // CANCELLING 주문에 delivery.cancelled.ack 수신 시 restore.stock.command가 발행되는지 검증
    @Test
    void onDeliveryCancelled_cancellingOrder_publishesRestoreStockCommand() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        ReflectionTestUtils.setField(order, "id", ORDER_ID);
        order.startCancelling(USER_ID, "단순 변심");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        orchestrator.onDeliveryCancelled(ORDER_ID);

        verify(orderEventPublisher).publishRestoreStockCommand(eq(ORDER_ID), any());
    }

    // 주문이 존재하지 않으면 예외 없이 무시하는지 검증 (Kafka 재시도 방지)
    @Test
    void onDeliveryCancelled_orderNotFound_noException() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatCode(() -> orchestrator.onDeliveryCancelled(ORDER_ID)).doesNotThrowAnyException();
    }

    // CANCELLING이 아닌 상태에서 수신 시 멱등성이 보장되는지 검증 (중복 이벤트)
    @Test
    void onDeliveryCancelled_notCancelling_idempotent() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        // PENDING 상태 — CANCELLING이 아님
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatCode(() -> orchestrator.onDeliveryCancelled(ORDER_ID)).doesNotThrowAnyException();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    // ===== onStockRestored =====

    // CANCELLING 주문에 stock.restored.ack 수신 시 CANCELLED로 확정되는지 검증
    @Test
    void onStockRestored_cancellingOrder_transitionsToCancelled() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        order.startCancelling(USER_ID, "단순 변심");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        orchestrator.onStockRestored(ORDER_ID);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isNotNull();
        // cancelledBy, cancelReason은 start() 시점에 이미 저장됨
        assertThat(order.getCancelledBy()).isEqualTo(USER_ID);
        assertThat(order.getCancelReason()).isEqualTo("단순 변심");
    }

    // 주문이 존재하지 않으면 예외 없이 무시하는지 검증 (Kafka 재시도 방지)
    @Test
    void onStockRestored_orderNotFound_noException() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatCode(() -> orchestrator.onStockRestored(ORDER_ID)).doesNotThrowAnyException();
    }

    // 이미 CANCELLED인 주문에 대해 멱등성이 보장되는지 검증 (중복 이벤트)
    @Test
    void onStockRestored_notCancelling_idempotent() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        order.cancel(null, "이미 취소됨"); // CANCELLED 상태
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatCode(() -> orchestrator.onStockRestored(ORDER_ID)).doesNotThrowAnyException();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo("이미 취소됨"); // 기존 사유 유지
    }
}
