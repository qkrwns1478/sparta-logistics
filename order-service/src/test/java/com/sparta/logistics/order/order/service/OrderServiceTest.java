package com.sparta.logistics.order.order.service;

import com.sparta.logistics.common.domain.Role;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.common.kafka.event.HubStockUpdatedEvent;
import com.sparta.logistics.order.kafka.producer.OrderEventPublisher;
import com.sparta.logistics.common.response.ApiResponse;
import com.sparta.logistics.order.client.CompanyServiceClient;
import com.sparta.logistics.order.client.ProductServiceClient;
import com.sparta.logistics.order.client.response.CompanyResponse;
import com.sparta.logistics.order.client.response.ProductResponse;
import com.sparta.logistics.order.exception.OrderErrorCode;
import com.sparta.logistics.order.order.dto.response.OrderDetailResponse;
import com.sparta.logistics.order.order.entity.Order;
import com.sparta.logistics.order.order.enums.OrderStatus;
import com.sparta.logistics.order.order.entity.OrderDelivery;
import com.sparta.logistics.order.order.lock.OrderLockManager;
import com.sparta.logistics.order.order.lock.OrderProcessStatus;
import com.sparta.logistics.order.order.repository.OrderDeliveryRepository;
import com.sparta.logistics.order.order.repository.OrderRepository;
import com.sparta.logistics.order.order.saga.CancelOrderOrchestrator;
import com.sparta.logistics.order.orderitem.dto.request.OrderItemRequest;
import com.sparta.logistics.order.stock.entity.ProductStockSnapshot;
import com.sparta.logistics.order.stock.repository.ProductStockSnapshotRepository;
import feign.FeignException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @InjectMocks
    private OrderService orderService;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private OrderDeliveryRepository orderDeliveryRepository;

    @Mock
    private CompanyServiceClient companyServiceClient;

    @Mock
    private ProductServiceClient productServiceClient;

    @Mock
    private OrderEventPublisher orderEventPublisher;

    @Mock
    private CancelOrderOrchestrator cancelOrderOrchestrator;

    @Mock
    private ProductStockSnapshotRepository snapshotRepository;

    @Mock
    private OrderLockManager orderLockManager;

    private final UUID REQUESTER_COMPANY_ID = UUID.randomUUID();
    private final UUID RECEIVER_COMPANY_ID = UUID.randomUUID();
    private final UUID PRODUCT_ID = UUID.randomUUID();
    private final UUID USER_ID = UUID.randomUUID();
    private final UUID ORDER_ID = UUID.randomUUID();
    private final UUID HUB_ID = UUID.randomUUID();
    private final UUID SOURCE_HUB_ID = UUID.randomUUID();
    private final UUID DESTINATION_HUB_ID = UUID.randomUUID();
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
        CompanyResponse requesterCompany = new CompanyResponse(REQUESTER_COMPANY_ID, "요청업체", "PRODUCER", SOURCE_HUB_ID, "출발허브", "주소", "ACTIVE");
        CompanyResponse receiverCompany = new CompanyResponse(RECEIVER_COMPANY_ID, "수령업체", "RECEIVER", DESTINATION_HUB_ID, "도착허브", "주소", "ACTIVE");
        when(companyServiceClient.getCompany(REQUESTER_COMPANY_ID)).thenReturn(ApiResponse.ok(requesterCompany));
        when(companyServiceClient.getCompany(RECEIVER_COMPANY_ID)).thenReturn(ApiResponse.ok(receiverCompany));
        OrderItemRequest itemRequest = mock(OrderItemRequest.class);
        when(itemRequest.getProductId()).thenReturn(PRODUCT_ID);
        when(itemRequest.getQuantity()).thenReturn(2);
        when(productServiceClient.getProducts(anyList())).thenReturn(ApiResponse.ok(List.of(product)));

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

        verify(companyServiceClient).getCompany(REQUESTER_COMPANY_ID);
        verify(companyServiceClient).getCompany(RECEIVER_COMPANY_ID);
        verify(orderRepository).save(any(Order.class));
        // order.created 이벤트가 Kafka로 발행되는지 검증
        verify(orderEventPublisher).publishOrderCreated(any(Order.class), any(UUID.class), any(UUID.class), any(String.class));
        assertThat(result).isNotNull();
    }

    // Company Service가 404를 반환하면 COMPANY_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void createOrder_companyNotFound_throwsException() {
        doThrow(mock(FeignException.NotFound.class))
                .when(companyServiceClient).getCompany(REQUESTER_COMPANY_ID);

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
                .when(companyServiceClient).getCompany(any());

        assertThatThrownBy(() ->
                orderService.createOrder(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, DUE_DATE, null, List.of(), USER_ID)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.COMPANY_SERVICE_UNAVAILABLE));
    }

    // 배치 응답에 요청한 상품 ID가 없으면 PRODUCT_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void createOrder_productNotFound_throwsException() {
        stubCompanyClients();
        OrderItemRequest itemRequest = mock(OrderItemRequest.class);
        when(itemRequest.getProductId()).thenReturn(PRODUCT_ID);
        when(itemRequest.getQuantity()).thenReturn(1);
        when(productServiceClient.getProducts(anyList())).thenReturn(ApiResponse.ok(List.of()));

        assertThatThrownBy(() ->
                orderService.createOrder(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, DUE_DATE, null, List.of(itemRequest), USER_ID)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.PRODUCT_NOT_FOUND));
    }

    // 상품 상태가 AVAILABLE이 아니면 PRODUCT_NOT_AVAILABLE 예외가 발생하는지 검증
    @Test
    void createOrder_productNotAvailable_throwsException() {
        stubCompanyClients();
        ProductResponse unavailable = new ProductResponse(
                PRODUCT_ID, "단종 상품", REQUESTER_COMPANY_ID, "업체",
                HUB_ID, "허브", 10_000L, "설명", "DISCONTINUED"
        );
        OrderItemRequest itemRequest = mock(OrderItemRequest.class);
        when(itemRequest.getProductId()).thenReturn(PRODUCT_ID);
        when(itemRequest.getQuantity()).thenReturn(1);
        when(productServiceClient.getProducts(anyList())).thenReturn(ApiResponse.ok(List.of(unavailable)));

        assertThatThrownBy(() ->
                orderService.createOrder(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, DUE_DATE, null, List.of(itemRequest), USER_ID)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.PRODUCT_NOT_AVAILABLE));
    }

    // Product Service 호출 중 네트워크 오류가 발생하면 PRODUCT_SERVICE_UNAVAILABLE 예외가 발생하는지 검증
    @Test
    void createOrder_productServiceUnavailable_throwsException() {
        stubCompanyClients();
        OrderItemRequest itemRequest = mock(OrderItemRequest.class);
        when(itemRequest.getProductId()).thenReturn(PRODUCT_ID);
        when(itemRequest.getQuantity()).thenReturn(1);
        when(productServiceClient.getProducts(anyList())).thenThrow(mock(FeignException.class));

        assertThatThrownBy(() ->
                orderService.createOrder(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, DUE_DATE, null, List.of(itemRequest), USER_ID)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.PRODUCT_SERVICE_UNAVAILABLE));
    }

    // 스냅샷 재고가 주문 수량보다 적으면 PRODUCT_STOCK_INSUFFICIENT 예외가 발생하는지 검증
    // Hub 서비스 호출 없이 즉시 반환되어야 함
    @Test
    void createOrder_snapshotStockInsufficient_throwsException() {
        stubCompanyClients();
        OrderItemRequest itemRequest = mock(OrderItemRequest.class);
        when(itemRequest.getProductId()).thenReturn(PRODUCT_ID);
        when(itemRequest.getQuantity()).thenReturn(10);

        ProductStockSnapshot snapshot = ProductStockSnapshot.create(PRODUCT_ID, HUB_ID, 5, 1L); // available=5 < requested=10
        when(snapshotRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(snapshot));

        assertThatThrownBy(() ->
                orderService.createOrder(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, DUE_DATE, null, List.of(itemRequest), USER_ID)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.PRODUCT_STOCK_INSUFFICIENT));

        // 재고 부족이 확정되면 Product Service 호출 없이 즉시 실패해야 함
        verify(productServiceClient, never()).getProduct(any());
    }

    // 해당 상품의 스냅샷이 없으면 사전 검증을 건너뛰고 주문 생성을 계속 진행하는지 검증
    @Test
    void createOrder_noSnapshot_skipsPreValidationAndProceeds() {
        stubCompanyClients();
        ProductResponse product = new ProductResponse(
                PRODUCT_ID, "테스트 상품", REQUESTER_COMPANY_ID, "업체명",
                HUB_ID, "허브명", 10_000L, "설명", "AVAILABLE"
        );
        OrderItemRequest itemRequest = mock(OrderItemRequest.class);
        when(itemRequest.getProductId()).thenReturn(PRODUCT_ID);
        when(itemRequest.getQuantity()).thenReturn(5);
        when(snapshotRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.empty()); // 스냅샷 없음
        when(productServiceClient.getProducts(anyList())).thenReturn(ApiResponse.ok(List.of(product)));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order savedOrder = invocation.getArgument(0);
            ReflectionTestUtils.setField(savedOrder, "id", ORDER_ID);
            return savedOrder;
        });

        OrderDetailResponse result = orderService.createOrder(
                REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, DUE_DATE, null, List.of(itemRequest), USER_ID
        );

        assertThat(result).isNotNull();
        verify(orderRepository).save(any(Order.class));
    }

    private void stubCompanyClients() {
        CompanyResponse stub = new CompanyResponse(REQUESTER_COMPANY_ID, "업체", "PRODUCER", HUB_ID, "허브", "주소", "ACTIVE");
        when(companyServiceClient.getCompany(any())).thenReturn(ApiResponse.ok(stub));
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
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

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
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

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
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(companyServiceClient.getCompany(any())).thenReturn(ApiResponse.ok(company));

        assertThat(orderService.getOrder(ORDER_ID, otherUserId, Role.MASTER)).isNotNull();
    }

    // COMPANY_MANAGER가 본인 주문을 조회하면 정상적으로 반환되는지 검증
    @Test
    void getOrder_ownOrder_allowsCompanyManagerAccess() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        CompanyResponse company = new CompanyResponse(REQUESTER_COMPANY_ID, "업체명", "PRODUCER", HUB_ID, "허브명", "서울", "ACTIVE");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
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
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

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
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

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
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
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
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

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
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(companyServiceClient.getCompany(REQUESTER_COMPANY_ID)).thenReturn(ApiResponse.ok(company));

        OrderDetailResponse result = orderService.updateOrder(ORDER_ID, null, "메모 수정", USER_ID, Role.HUB_MANAGER, HUB_ID);

        assertThat(result).isNotNull();
        assertThat(order.getRequestMemo()).isEqualTo("메모 수정");
    }

    // CANCELLING 상태인 주문 수정 시도 시 ORDER_ALREADY_CANCELLING 예외가 발생하는지 검증
    @Test
    void updateOrder_whenCancelling_throwsAlreadyCancelling() {
        when(orderLockManager.getStatusKey(ORDER_ID)).thenReturn(Optional.of(OrderProcessStatus.CANCELLING));

        assertThatThrownBy(() ->
                orderService.updateOrder(ORDER_ID, DUE_DATE, null, USER_ID, Role.MASTER, null)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_ALREADY_CANCELLING));
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
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                orderService.cancelOrder(ORDER_ID, "사유", USER_ID, Role.MASTER, null)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }

    // 이미 취소된 주문은 isModifiable() 검사에서 ORDER_NOT_CANCELLABLE 예외가 발생하는지 검증
    @Test
    void cancelOrder_alreadyCancelled_throwsException() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        order.cancel(USER_ID, "이미 취소됨");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        // 오케스트레이터 호출 이전에 서비스 자체에서 예외를 던지므로 stub 불필요
        // 실제 상태 전이 검사(Order.startCancelling -> canTransitionTo)는 CancelOrderOrchestratorTest에서 검증

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
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(companyServiceClient.getCompany(REQUESTER_COMPANY_ID)).thenReturn(ApiResponse.ok(company));

        assertThatThrownBy(() ->
                orderService.cancelOrder(ORDER_ID, "취소 사유", USER_ID, Role.HUB_MANAGER, HUB_ID)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_HUB_ACCESS_DENIED));
    }

    // MASTER 역할로 주문 취소 요청 시 CancelOrderOrchestrator.start()가 호출되는지 검증
    @Test
    void cancelOrder_masterRole_delegatesToOrchestrator() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        OrderDetailResponse result = orderService.cancelOrder(ORDER_ID, "단순 변심", USER_ID, Role.MASTER, null);

        assertThat(result).isNotNull();
        // CANCELLING 전이 + Kafka 발행은 CancelOrderOrchestrator가 담당 (CancelOrderOrchestratorTest에서 검증)
        verify(cancelOrderOrchestrator).start(eq(order), eq(USER_ID), eq("단순 변심"));
    }

    // HUB_MANAGER가 담당 허브의 주문 취소 요청 시 오케스트레이터에 올바른 인자가 전달되는지 검증
    @Test
    void cancelOrder_hubManagerSameHub_delegatesToOrchestrator() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        CompanyResponse company = new CompanyResponse(REQUESTER_COMPANY_ID, "업체", "PRODUCER", HUB_ID, "허브", "서울", "ACTIVE");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(companyServiceClient.getCompany(REQUESTER_COMPANY_ID)).thenReturn(ApiResponse.ok(company));

        OrderDetailResponse result = orderService.cancelOrder(ORDER_ID, "재고 소진", USER_ID, Role.HUB_MANAGER, HUB_ID);

        assertThat(result).isNotNull();
        verify(cancelOrderOrchestrator).start(eq(order), eq(USER_ID), eq("재고 소진"));
    }

    // 상태키가 PROCESSING이면 취소 시 ORDER_PROCESSING_IN_PROGRESS 예외가 발생하는지 검증
    @Test
    void cancelOrder_whenProcessing_throwsProcessingInProgress() {
        when(orderLockManager.getStatusKey(ORDER_ID)).thenReturn(Optional.of(OrderProcessStatus.PROCESSING));

        assertThatThrownBy(() ->
                orderService.cancelOrder(ORDER_ID, "사유", USER_ID, Role.MASTER, null)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_PROCESSING_IN_PROGRESS));
    }

    // 상태키가 CANCELLING이면 취소 시 ORDER_ALREADY_CANCELLING 예외가 발생하는지 검증
    @Test
    void cancelOrder_whenAlreadyCancelling_throwsAlreadyCancelling() {
        when(orderLockManager.getStatusKey(ORDER_ID)).thenReturn(Optional.of(OrderProcessStatus.CANCELLING));

        assertThatThrownBy(() ->
                orderService.cancelOrder(ORDER_ID, "사유", USER_ID, Role.MASTER, null)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_ALREADY_CANCELLING));
    }

    // 분산 락 획득 실패 시 ORDER_LOCK_CONFLICT 예외가 발생하는지 검증
    @Test
    void cancelOrder_lockAcquireFail_throwsLockConflict() {
        doThrow(new BusinessException(OrderErrorCode.ORDER_LOCK_CONFLICT))
                .when(orderLockManager).acquireLock(ORDER_ID);

        assertThatThrownBy(() ->
                orderService.cancelOrder(ORDER_ID, "사유", USER_ID, Role.MASTER, null)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_LOCK_CONFLICT));
    }

    // ===== acceptOrder (Choreography Saga Step 1-4) =====

    // delivery.created 수신 건수가 totalDeliveryCount에 도달하면 ACCEPTED로 전이되고 deliveryId가 저장되는지 검증
    @Test
    void acceptOrder_allDeliveriesReceived_transitionsToAccepted() {
        UUID deliveryId = UUID.randomUUID();
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderDeliveryRepository.countByOrderId(ORDER_ID)).thenReturn(1L); // 1/1 수신 완료

        orderService.acceptOrder(ORDER_ID, deliveryId, 1);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        assertThat(order.getDeliveryId()).isEqualTo(deliveryId);
        verify(orderDeliveryRepository).save(any(OrderDelivery.class));
    }

    // delivery.created 수신 건수가 totalDeliveryCount 미만이면 PENDING을 유지하는지 검증 (다중 배송)
    @Test
    void acceptOrder_partialDeliveries_remainsPending() {
        UUID deliveryId = UUID.randomUUID();
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));
        when(orderDeliveryRepository.countByOrderId(ORDER_ID)).thenReturn(1L); // 1/2 수신 중

        orderService.acceptOrder(ORDER_ID, deliveryId, 2);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        verify(orderDeliveryRepository).save(any(OrderDelivery.class));
    }

    // 주문이 존재하지 않으면 예외 없이 무시하는지 검증 (Kafka 재시도 방지)
    @Test
    void acceptOrder_orderNotFound_noException() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        // 예외가 발생하지 않아야 함 — Kafka 불필요한 재시도 방지
        assertThatCode(() -> orderService.acceptOrder(ORDER_ID, UUID.randomUUID(), 1))
                .doesNotThrowAnyException();
    }

    // 이미 ACCEPTED 상태인 주문에 대해 멱등성이 보장되는지 검증 (중복 이벤트 처리)
    @Test
    void acceptOrder_alreadyAccepted_idempotent() {
        UUID deliveryId = UUID.randomUUID();
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        order.accept();                      // 이미 ACCEPTED
        order.linkDelivery(deliveryId);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        // 중복 이벤트여도 예외 없이 무시되어야 함
        // status != PENDING이므로 early return
        assertThatCode(() -> orderService.acceptOrder(ORDER_ID, deliveryId, 1))
                .doesNotThrowAnyException();
        assertThat(order.getStatus()).isEqualTo(OrderStatus.ACCEPTED);
        verify(orderDeliveryRepository, never()).save(any());
    }

    // ===== cancelOrderByCompensation (Choreography Saga 보상) =====

    // PENDING 주문이 보상 취소 수신 시 즉시 CANCELLED 처리되는지 검증 (stock.reservation.failed)
    @Test
    void cancelOrderByCompensation_pendingOrder_cancelled() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

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
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.cancelOrderByCompensation(ORDER_ID, "배송 생성 실패");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelReason()).isEqualTo("배송 생성 실패");
    }

    // 이미 CANCELLED인 주문에 대해 멱등성이 보장되는지 검증 (중복 이벤트)
    @Test
    void cancelOrderByCompensation_alreadyCancelled_idempotent() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        order.cancel(null, "이미 취소됨");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatCode(() -> orderService.cancelOrderByCompensation(ORDER_ID, "중복 이벤트"))
                .doesNotThrowAnyException();
        assertThat(order.getCancelReason()).isEqualTo("이미 취소됨"); // 기존 사유 유지
    }

    // 주문이 존재하지 않으면 예외 없이 무시하는지 검증 (Kafka 재시도 방지)
    @Test
    void cancelOrderByCompensation_orderNotFound_noException() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatCode(() -> orderService.cancelOrderByCompensation(ORDER_ID, "재고 부족"))
                .doesNotThrowAnyException();
    }

    // ===== handleDeliveryCancelled (Orchestration Saga Step 3-3) =====

    // delivery.cancelled.ack 수신 시 CancelOrderOrchestrator.onDeliveryCancelled()에 위임하는지 검증
    // CANCELLING 상태 검사, restore.stock.command 발행 등 상세 동작은 CancelOrderOrchestratorTest에서 검증
    @Test
    void handleDeliveryCancelled_delegatesToOrchestrator() {
        orderService.handleDeliveryCancelled(ORDER_ID);

        verify(cancelOrderOrchestrator).onDeliveryCancelled(ORDER_ID);
    }

    // ===== confirmOrderCancelled (Orchestration Saga Step 3-5) =====

    // stock.restored.ack 수신 시 CancelOrderOrchestrator.onStockRestored()에 위임하는지 검증
    // CANCELLED 확정, 멱등성 등 상세 동작은 CancelOrderOrchestratorTest에서 검증
    @Test
    void confirmOrderCancelled_delegatesToOrchestrator() {
        orderService.confirmOrderCancelled(ORDER_ID);

        verify(cancelOrderOrchestrator).onStockRestored(ORDER_ID);
    }

    // Orchestration Saga 완료 시 CANCELLING 상태 키가 해제되는지 검증
    @Test
    void confirmOrderCancelled_clearsCancellingStatusKey() {
        orderService.confirmOrderCancelled(ORDER_ID);

        verify(orderLockManager).clearStatusKey(ORDER_ID);
    }

    // ===== handleDeliveryCancellationFailed (Orchestration Saga 보상 Step 4-1) =====

    // delivery.cancellation.failed 수신 시 CancelOrderOrchestrator.onDeliveryCancellationFailed()에 위임하는지 검증
    @Test
    void handleDeliveryCancellationFailed_delegatesToOrchestrator() {
        orderService.handleDeliveryCancellationFailed(ORDER_ID);

        verify(cancelOrderOrchestrator).onDeliveryCancellationFailed(ORDER_ID);
    }

    // Saga 복구(배송 취소 실패) 시 CANCELLING 상태 키가 해제되는지 검증
    // 복구 후 주문 상태가 PENDING/ACCEPTED로 돌아가므로 후속 Consumer 이벤트가 정상 처리될 수 있어야 함
    @Test
    void handleDeliveryCancellationFailed_clearsCancellingStatusKey() {
        orderService.handleDeliveryCancellationFailed(ORDER_ID);

        verify(orderLockManager).clearStatusKey(ORDER_ID);
    }

    // ===== cancelOrder 동시성 제어 =====

    // 검증 통과 후 CANCELLING 키가 세팅되고, 성공 시 clearStatusKey가 호출되지 않는지 검증
    // (CANCELLING 키는 Saga 완료/복구 시점에 해제됨)
    @Test
    void cancelOrder_success_setsCancellingKeyAfterValidation_doesNotClear() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.cancelOrder(ORDER_ID, "단순 변심", USER_ID, Role.MASTER, null);

        verify(orderLockManager).setStatusKey(ORDER_ID, OrderProcessStatus.CANCELLING);
        verify(orderLockManager, never()).clearStatusKey(ORDER_ID);
    }

    // 검증 실패(비취소 가능 상태) 시 CANCELLING 키가 세팅되지 않는지 검증
    @Test
    void cancelOrder_validationFails_doesNotSetCancellingKey() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        order.cancel(USER_ID, "이미 취소됨");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                orderService.cancelOrder(ORDER_ID, "재취소 시도", USER_ID, Role.MASTER, null)
        ).isInstanceOf(BusinessException.class);

        verify(orderLockManager, never()).setStatusKey(any(), eq(OrderProcessStatus.CANCELLING));
    }

    // ===== syncSnapshot =====

    // 해당 productId의 스냅샷이 없을 때 신규 스냅샷이 생성되는지 검증
    @Test
    void syncSnapshot_newProduct_createsSnapshot() {
        HubStockUpdatedEvent event = HubStockUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .productId(PRODUCT_ID)
                .hubId(HUB_ID)
                .available(200)
                .hubStockVersion(1L)
                .build();

        when(snapshotRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.empty());

        orderService.syncSnapshot(event);

        verify(snapshotRepository).save(any(ProductStockSnapshot.class));
    }

    // 기존 스냅샷이 있고 이벤트 버전이 더 최신이면 스냅샷이 갱신되는지 검증
    @Test
    void syncSnapshot_existingProduct_newerVersion_updatesSnapshot() {
        ProductStockSnapshot existing = ProductStockSnapshot.create(PRODUCT_ID, HUB_ID, 100, 2L);
        HubStockUpdatedEvent event = HubStockUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .productId(PRODUCT_ID)
                .hubId(HUB_ID)
                .available(150)
                .hubStockVersion(3L)  // 기존 버전(2) 보다 최신
                .build();

        when(snapshotRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(existing));

        orderService.syncSnapshot(event);

        assertThat(existing.getAvailable()).isEqualTo(150);
        assertThat(existing.getHubStockVersion()).isEqualTo(3L);
        // 신규 저장은 불필요 — JPA dirty checking으로 처리됨
        verify(snapshotRepository, never()).save(any());
    }

    // 이벤트 버전이 저장된 버전보다 낮거나 같으면 구버전으로 간주하고 스냅샷을 갱신하지 않는지 검증
    @Test
    void syncSnapshot_existingProduct_olderOrSameVersion_ignored() {
        ProductStockSnapshot existing = ProductStockSnapshot.create(PRODUCT_ID, HUB_ID, 100, 5L);
        int originalAvailable = existing.getAvailable();

        HubStockUpdatedEvent sameVersionEvent = HubStockUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .productId(PRODUCT_ID)
                .hubId(HUB_ID)
                .available(999)
                .hubStockVersion(5L)  // 같은 버전 → 무시
                .build();

        when(snapshotRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.of(existing));

        orderService.syncSnapshot(sameVersionEvent);

        // 갱신 없이 기존 available 유지
        assertThat(existing.getAvailable()).isEqualTo(originalAvailable);
        verify(snapshotRepository, never()).save(any());
    }

    // 이벤트 수신 시 예외 없이 처리되는지 검증 (Kafka 재시도 방지)
    @Test
    void syncSnapshot_noException_onAnyInput() {
        HubStockUpdatedEvent event = HubStockUpdatedEvent.builder()
                .eventId(UUID.randomUUID())
                .productId(PRODUCT_ID)
                .hubId(HUB_ID)
                .available(50)
                .hubStockVersion(1L)
                .build();

        when(snapshotRepository.findByProductId(PRODUCT_ID)).thenReturn(Optional.empty());

        assertThatCode(() -> orderService.syncSnapshot(event)).doesNotThrowAnyException();
    }

    // ===== deleteOrder =====

    // MASTER 이외의 역할로 삭제 시도 시 ORDER_DELETE_PERMISSION_DENIED 예외가 발생하는지 검증
    @Test
    void deleteOrder_nonMasterRole_throwsPermissionDenied() {
        assertThatThrownBy(() ->
                orderService.deleteOrder(ORDER_ID, USER_ID, Role.HUB_MANAGER)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_DELETE_PERMISSION_DENIED));
    }

    // 존재하지 않는 주문 삭제 시도 시 ORDER_NOT_FOUND 예외가 발생하는지 검증
    @Test
    void deleteOrder_orderNotFound_throwsException() {
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                orderService.deleteOrder(ORDER_ID, USER_ID, Role.MASTER)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_NOT_FOUND));
    }

    // PENDING 상태의 주문 삭제 시도 시 ORDER_NOT_DELETABLE 예외가 발생하는지 검증
    @Test
    void deleteOrder_pendingOrder_throwsNotDeletable() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        assertThatThrownBy(() ->
                orderService.deleteOrder(ORDER_ID, USER_ID, Role.MASTER)
        ).isInstanceOf(BusinessException.class)
         .satisfies(e -> assertThat(((BusinessException) e).getErrorCode())
                 .isEqualTo(OrderErrorCode.ORDER_NOT_DELETABLE));
    }

    // CANCELLED 상태의 주문을 MASTER가 삭제하면 soft delete가 적용되는지 검증
    @Test
    void deleteOrder_cancelledOrder_success() {
        Order order = Order.create(REQUESTER_COMPANY_ID, RECEIVER_COMPANY_ID, USER_ID, DUE_DATE, null);
        order.cancel(USER_ID, "단순 변심");
        when(orderRepository.findById(ORDER_ID)).thenReturn(Optional.of(order));

        orderService.deleteOrder(ORDER_ID, USER_ID, Role.MASTER);

        assertThat(order.isDeleted()).isTrue();
    }
}
