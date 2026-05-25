package com.sparta.logistics.order.order.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.kafka.KafkaTopics;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.order.client.CompanyServiceClient;
import com.sparta.logistics.order.client.ProductServiceClient;
import com.sparta.logistics.order.client.response.CompanyResponse;
import com.sparta.logistics.order.client.response.ProductResponse;
import com.sparta.logistics.order.exception.OrderErrorCode;
import com.sparta.logistics.order.order.dto.response.OrderDetailResponse;
import com.sparta.logistics.order.order.entity.Order;
import com.sparta.logistics.order.order.enums.OrderStatus;
import com.sparta.logistics.order.order.repository.OrderRepository;
import com.sparta.logistics.order.orderitem.dto.request.OrderItemRequest;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private CompanyServiceClient companyServiceClient;

    @Mock
    private ProductServiceClient productServiceClient;

    @SuppressWarnings("rawtypes")
    @Mock
    private KafkaTemplate kafkaTemplate;

    private final UUID REQUESTER_COMPANY_ID = UUID.randomUUID();
    private final UUID RECEIVER_COMPANY_ID = UUID.randomUUID();
    private final UUID PRODUCT_ID = UUID.randomUUID();
    private final UUID USER_ID = UUID.randomUUID();
    private final UUID ORDER_ID = UUID.randomUUID();
    private final UUID HUB_ID = UUID.randomUUID();
    private final LocalDateTime DUE_DATE = LocalDateTime.now().plusDays(7);

    // ===== createOrder =====

    // 정상 요청 시 업체/상품 검증 후 주문이 저장되고, order.created 이벤트가 발행되며, 응답이 반환되는지 검증
    @Test
    @SuppressWarnings("unchecked")
    void createOrder_success() {
        ProductResponse product = new ProductResponse(
                PRODUCT_ID, "테스트 상품", REQUESTER_COMPANY_ID, "업체명",
                HUB_ID, "허브명", 10_000L, "설명", "AVAILABLE"
        );
        OrderItemRequest itemRequest = mock(OrderItemRequest.class);
        when(itemRequest.getProductId()).thenReturn(PRODUCT_ID);
        when(itemRequest.getQuantity()).thenReturn(2);
        when(productServiceClient.getProduct(PRODUCT_ID)).thenReturn(ApiResponse.ok(product));

        // Mock save()가 JPA UUID 생성을 대신 수행하도록 설정
        // 실제 JPA는 save() 시 @GeneratedValue(UUID)로 ID를 할당하지만, Mock은 그렇지 않음
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order savedOrder = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedOrder, "id", ORDER_ID);
            return savedOrder;
        });

        OrderDetailResponse result = orderService.createOrder(
                REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, DUE_DATE, "메모", List.of(itemRequest), USER_ID
        );

        verify(companyServiceClient).checkCompanyExists(REQUESTER_COMPANY_ID);
        verify(companyServiceClient).checkCompanyExists(RECEIVER_COMPANY_ID);
        verify(orderRepository).save(any(Order.class));
        // order.created 이벤트가 orderId 파티션 키와 함께 Kafka로 발행되는지 검증
        verify(kafkaTemplate).send(eq(KafkaTopics.ORDER_CREATED), eq(ORDER_ID.toString()), any());
        assertThat(result).isNotNull();
    }

    // Company Service가 404를 반환하면 COMPANY_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void createOrder_companyNotFound_throwsException() {
        doThrow(mock(FeignException.NotFound.class))
                .when(companyServiceClient).checkCompanyExists(REQUESTER_COMPANY_ID);

        assertThatThrownBy(() ->
                orderService.createOrder(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, DUE_DATE, null, List.of(), USER_ID)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.COMPANY_NOT_FOUND));
    }

    // Company Service 호출 중 네트워크 오류가 발생하면 COMPANY_SERVICE_UNAVAILABLE 예외가 발생하는지 검증
    @Test
    void createOrder_companyServiceUnavailable_throwsException() {
        doThrow(mock(FeignException.class))
                .when(companyServiceClient).checkCompanyExists(any());

        assertThatThrownBy(() ->
                orderService.createOrder(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, DUE_DATE, null, List.of(), USER_ID)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.COMPANY_SERVICE_UNAVAILABLE));
    }

    // Product Service가 404를 반환하면 PRODUCT_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void createOrder_productNotFound_throwsException() {
        OrderItemRequest itemRequest = mock(OrderItemRequest.class);
        when(itemRequest.getProductId()).thenReturn(PRODUCT_ID);
        when(productServiceClient.getProduct(PRODUCT_ID)).thenThrow(mock(FeignException.NotFound.class));

        assertThatThrownBy(() ->
                orderService.createOrder(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, DUE_DATE, null, List.of(itemRequest), USER_ID)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.PRODUCT_NOT_FOUND));
    }

    // 상품 상태가 AVAILABLE이 아니면 PRODUCT_NOT_AVAILABLE 예외가 발생하는지 검증
    @Test
    void createOrder_productNotAvailable_throwsException() {
        ProductResponse unavailable = new ProductResponse(
                PRODUCT_ID, "단종 상품", REQUESTER_COMPANY_ID, "업체",
                HUB_ID, "허브", 10_000L, "설명", "DISCONTINUED"
        );
        OrderItemRequest itemRequest = mock(OrderItemRequest.class);
        when(itemRequest.getProductId()).thenReturn(PRODUCT_ID);
        when(productServiceClient.getProduct(PRODUCT_ID)).thenReturn(ApiResponse.ok(unavailable));

        assertThatThrownBy(() ->
                orderService.createOrder(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, DUE_DATE, null, List.of(itemRequest), USER_ID)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.PRODUCT_NOT_AVAILABLE));
    }

    // Product Service 호출 중 네트워크 오류가 발생하면 PRODUCT_SERVICE_UNAVAILABLE 예외가 발생하는지 검증
    @Test
    void createOrder_productServiceUnavailable_throwsException() {
        OrderItemRequest itemRequest = mock(OrderItemRequest.class);
        when(itemRequest.getProductId()).thenReturn(PRODUCT_ID);
        when(productServiceClient.getProduct(PRODUCT_ID)).thenThrow(mock(FeignException.class));

        assertThatThrownBy(() ->
                orderService.createOrder(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, DUE_DATE, null, List.of(itemRequest), USER_ID)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE));
    }

    // ===== getOrders =====

    // MASTER 역할은 userId 필터 없이 전체 주문을 조회하는지 검증
    @Test
    void getOrders_masterRole_searchesWithNullUserId() {
        when(orderRepository.search(isNull(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        orderService.getOrders(null, null, null, null, null, Role.MASTER, USER_ID, Pageable.unpaged());

        verify(orderRepository).search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any());
    }

    // HUB_MANAGER 역할도 userId 필터 없이 전체 주문을 조회하는지 검증
    @Test
    void getOrders_hubManagerRole_searchesWithNullUserId() {
        when(orderRepository.search(isNull(), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        orderService.getOrders(null, null, null, null, null, Role.HUB_MANAGER, USER_ID, Pageable.unpaged());

        verify(orderRepository).search(isNull(), isNull(), isNull(), isNull(), isNull(), isNull(), any());
    }

    // COMPANY_MANAGER 역할은 본인 userId로 필터링하여 주문을 조회하는지 검증
    @Test
    void getOrders_companyManagerRole_searchesWithUserId() {
        when(orderRepository.search(eq(USER_ID), any(), any(), any(), any(), any(), any()))
                .thenReturn(Page.empty());

        orderService.getOrders(null, null, null, null, null, Role.COMPANY_MANAGER, USER_ID, Pageable.unpaged());

        verify(orderRepository).search(eq(USER_ID), isNull(), isNull(), isNull(), isNull(), isNull(), any());
    }

    // ===== getOrder =====

    // 존재하지 않는 주문 ID로 조회 시 ORDER_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void getOrder_orderNotFound_throwsException() {
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                orderService.getOrder(ORDER_ID, USER_ID, Role.MASTER)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }

    // 일반 사용자가 타인의 주문을 조회하면 ORDER_ACCESS_DENIED 예외가 발생하는지 검증
    @Test
    void getOrder_otherUsersOrder_throwsAccessDenied() {
        UUID otherUserId = UUID.randomUUID();
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                orderService.getOrder(ORDER_ID, otherUserId, Role.COMPANY_MANAGER)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_ACCESS_DENIED));
    }

    // MASTER 역할은 타인 주문도 조회할 수 있는지 검증
    @Test
    void getOrder_masterRole_allowsAccessToAnyOrder() {
        UUID otherUserId = UUID.randomUUID();
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        CompanyResponse company = new CompanyResponse(REQUESTER_COMPANY_ID, "업체명", "PRODUCER", HUB_ID, "허브명", "서울", "ACTIVE");
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.of(order));
        when(companyServiceClient.getCompany(any())).thenReturn(ApiResponse.ok(company));

        assertThat(orderService.getOrder(ORDER_ID, otherUserId, Role.MASTER)).isNotNull();
    }

    // COMPANY_MANAGER가 본인 주문을 조회하면 정상적으로 반환되는지 검증
    @Test
    void getOrder_ownOrder_allowsCompanyManagerAccess() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        CompanyResponse company = new CompanyResponse(REQUESTER_COMPANY_ID, "업체명", "PRODUCER", HUB_ID, "허브명", "서울", "ACTIVE");
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.of(order));
        when(companyServiceClient.getCompany(any())).thenReturn(ApiResponse.ok(company));

        assertThat(orderService.getOrder(ORDER_ID, USER_ID, Role.COMPANY_MANAGER)).isNotNull();
    }

    // ===== updateOrder =====

    // COMPANY_MANAGER 역할로 수정 시도 시 ORDER_UPDATE_PERMISSION_DENIED 예외가 발생하는지 검증
    @Test
    void updateOrder_companyManagerRole_throwsPermissionDenied() {
        assertThatThrownBy(() ->
                orderService.updateOrder(ORDER_ID, DUE_DATE, null, USER_ID, Role.COMPANY_MANAGER, HUB_ID)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_UPDATE_PERMISSION_DENIED));
    }

    // 존재하지 않는 주문 수정 시도 시 ORDER_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void updateOrder_orderNotFound_throwsException() {
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                orderService.updateOrder(ORDER_ID, DUE_DATE, null, USER_ID, Role.MASTER, null)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }

    // CANCELLED 등 수정 불가 상태의 주문 수정 시도 시 ORDER_NOT_MODIFIABLE 예외가 발생하는지 검증
    @Test
    void updateOrder_nonModifiableStatus_throwsException() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        order.cancel(USER_ID, "취소됨");
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                orderService.updateOrder(ORDER_ID, DUE_DATE, null, USER_ID, Role.MASTER, null)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_NOT_MODIFIABLE));
    }

    // HUB_MANAGER가 담당하지 않는 허브의 주문 수정 시도 시 ORDER_HUB_ACCESS_DENIED 예외가 발생하는지 검증
    @Test
    void updateOrder_hubManagerDifferentHub_throwsHubAccessDenied() {
        UUID otherHubId = UUID.randomUUID();
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        CompanyResponse company = new CompanyResponse(REQUESTER_COMPANY_ID, "업체", "PRODUCER", otherHubId, "다른허브", "서울", "ACTIVE");
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.of(order));
        when(companyServiceClient.getCompany(REQUESTER_COMPANY_ID)).thenReturn(ApiResponse.ok(company));

        assertThatThrownBy(() ->
                orderService.updateOrder(ORDER_ID, DUE_DATE, null, USER_ID, Role.HUB_MANAGER, HUB_ID)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_HUB_ACCESS_DENIED));
    }

    // MASTER 역할로 주문 수정 시 dueDate와 requestMemo가 정상 반영되는지 검증
    @Test
    void updateOrder_masterRole_success() {
        LocalDateTime newDueDate = DUE_DATE.plusDays(1);
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.of(order));

        OrderDetailResponse result = orderService.updateOrder(ORDER_ID, newDueDate, "새 메모", USER_ID, Role.MASTER, null);

        assertThat(result).isNotNull();
        assertThat(order.getDueDate()).isEqualTo(newDueDate);
        assertThat(order.getRequestMemo()).isEqualTo("새 메모");
    }

    // HUB_MANAGER가 담당 허브의 주문을 수정하면 정상 처리되는지 검증
    @Test
    void updateOrder_hubManagerSameHub_success() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        CompanyResponse company = new CompanyResponse(REQUESTER_COMPANY_ID, "업체", "PRODUCER", HUB_ID, "허브", "서울", "ACTIVE");
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.of(order));
        when(companyServiceClient.getCompany(REQUESTER_COMPANY_ID)).thenReturn(ApiResponse.ok(company));

        OrderDetailResponse result = orderService.updateOrder(ORDER_ID, null, "메모 수정", USER_ID, Role.HUB_MANAGER, HUB_ID);

        assertThat(result).isNotNull();
        assertThat(order.getRequestMemo()).isEqualTo("메모 수정");
    }

    // ===== cancelOrder =====

    // COMPANY_MANAGER 역할로 취소 시도 시 ORDER_CANCEL_PERMISSION_DENIED 예외가 발생하는지 검증
    @Test
    void cancelOrder_companyManagerRole_throwsPermissionDenied() {
        assertThatThrownBy(() ->
                orderService.cancelOrder(ORDER_ID, "사유", USER_ID, Role.COMPANY_MANAGER, HUB_ID)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_CANCEL_PERMISSION_DENIED));
    }

    // 존재하지 않는 주문 취소 시도 시 ORDER_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void cancelOrder_orderNotFound_throwsException() {
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                orderService.cancelOrder(ORDER_ID, "사유", USER_ID, Role.MASTER, null)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }

    // 이미 취소된 주문을 다시 취소 시도 시 ORDER_NOT_CANCELLABLE 예외가 발생하는지 검증
    @Test
    void cancelOrder_alreadyCancelled_throwsException() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        order.cancel(USER_ID, "이미 취소됨");
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                orderService.cancelOrder(ORDER_ID, "재취소 시도", USER_ID, Role.MASTER, null)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_NOT_CANCELLABLE));
    }

    // HUB_MANAGER가 담당하지 않는 허브의 주문 취소 시도 시 ORDER_HUB_ACCESS_DENIED 예외가 발생하는지 검증
    @Test
    void cancelOrder_hubManagerDifferentHub_throwsHubAccessDenied() {
        UUID otherHubId = UUID.randomUUID();
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        CompanyResponse company = new CompanyResponse(REQUESTER_COMPANY_ID, "업체", "PRODUCER", otherHubId, "다른허브", "서울", "ACTIVE");
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.of(order));
        when(companyServiceClient.getCompany(REQUESTER_COMPANY_ID)).thenReturn(ApiResponse.ok(company));

        assertThatThrownBy(() ->
                orderService.cancelOrder(ORDER_ID, "취소 사유", USER_ID, Role.HUB_MANAGER, HUB_ID)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_HUB_ACCESS_DENIED));
    }

    // MASTER 역할로 주문 취소 시 상태가 CANCELLED로 변경되고 취소 정보가 기록되는지 검증
    @Test
    void cancelOrder_masterRole_success() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.of(order));

        OrderDetailResponse result = orderService.cancelOrder(ORDER_ID, "단순 변심", USER_ID, Role.MASTER, null);

        assertThat(result).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo("단순 변심");
        assertThat(order.getCancelledBy()).isEqualTo(USER_ID);
    }

    // HUB_MANAGER가 담당 허브의 주문을 취소하면 정상 처리되는지 검증
    @Test
    void cancelOrder_hubManagerSameHub_success() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        CompanyResponse company = new CompanyResponse(REQUESTER_COMPANY_ID, "업체", "PRODUCER", HUB_ID, "허브", "서울", "ACTIVE");
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.of(order));
        when(companyServiceClient.getCompany(REQUESTER_COMPANY_ID)).thenReturn(ApiResponse.ok(company));

        OrderDetailResponse result = orderService.cancelOrder(ORDER_ID, "재고 소진", USER_ID, Role.HUB_MANAGER, HUB_ID);

        assertThat(result).isNotNull();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    // ===== acceptOrder (Choreography Saga Step 1-4) =====

    // delivery.created 이벤트 수신 시 PENDING 주문이 ACCEPTED로 전이되고 deliveryId가 저장되는지 검증
    @Test
    void acceptOrder_pendingOrder_transitionsToAccepted() {
        UUID deliveryId = UUID.randomUUID();
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.acceptOrder(ORDER_ID, deliveryId);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getDeliveryId()).isEqualTo(deliveryId);
    }

    // 주문이 존재하지 않으면 예외 없이 무시하는지 검증 (Kafka 재시도 방지)
    @Test
    void acceptOrder_orderNotFound_noException() {
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.empty());

        // 예외가 발생하지 않아야 함 — Kafka 불필요한 재시도 방지
        assertThatCode(() -> orderService.acceptOrder(ORDER_ID, UUID.randomUUID()))
                .doesNotThrowAnyException();
    }

    // 이미 ACCEPTED 상태인 주문에 대해 멱등성이 보장되는지 검증 (중복 이벤트 처리)
    @Test
    void acceptOrder_alreadyAccepted_idempotent() {
        UUID deliveryId = UUID.randomUUID();
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        order.accept();                      // 이미 ACCEPTED
        order.linkDelivery(deliveryId);
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.of(order));

        // 중복 이벤트여도 예외 없이 무시되어야 함
        assertThatCode(() -> orderService.acceptOrder(ORDER_ID, deliveryId))
                .doesNotThrowAnyException();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
    }

    // ===== cancelOrderByCompensation (Choreography Saga 보상) =====

    // PENDING 주문이 보상 취소 수신 시 즉시 CANCELLED 처리되는지 검증 (stock.reservation.failed)
    @Test
    void cancelOrderByCompensation_pendingOrder_cancelled() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.cancelOrderByCompensation(ORDER_ID, "재고 부족");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo("재고 부족");
        assertThat(order.getCancelledBy()).isNull(); // 보상 취소는 사람이 아닌 시스템 발생이므로 null
    }

    // ACCEPTED 주문도 보상 취소 가능한지 검증 (delivery.creation.failed)
    @Test
    void cancelOrderByCompensation_acceptedOrder_cancelled() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        order.accept();
        order.linkDelivery(UUID.randomUUID());
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.cancelOrderByCompensation(ORDER_ID, "배송 생성 실패");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo("배송 생성 실패");
    }

    // 이미 CANCELLED인 주문에 대해 멱등성이 보장되는지 검증 (중복 이벤트)
    @Test
    void cancelOrderByCompensation_alreadyCancelled_idempotent() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        order.cancel(null, "이미 취소됨");
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatCode(() -> orderService.cancelOrderByCompensation(ORDER_ID, "중복 이벤트"))
                .doesNotThrowAnyException();
        assertThat(order.getCancelReason()).isEqualTo("이미 취소됨"); // 기존 사유 유지
    }

    // 주문이 존재하지 않으면 예외 없이 무시하는지 검증 (Kafka 재시도 방지)
    @Test
    void cancelOrderByCompensation_orderNotFound_noException() {
        when(orderRepository.findByIdAndDeletedAtIsNull(ORDER_ID)).thenReturn(Optional.empty());

        assertThatCode(() -> orderService.cancelOrderByCompensation(ORDER_ID, "재고 부족"))
                .doesNotThrowAnyException();
    }
}
