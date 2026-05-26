# Saga 패턴 의사결정

- **주문 생성**: Choreography Saga (Kafka 이벤트 체이닝)
- **주문 취소**: Orchestration Saga (Order 도메인의 Orchestrator)

---

## Kafka 토픽 전체 목록

| 토픽 | Publisher | Subscriber | 패턴 |
| --- | --- | --- | --- |
| `order.created` | OrderService | HubService | Choreography |
| `stock.reserved` | HubService | DeliveryService | Choreography |
| `stock.reservation.failed` | HubService | OrderService | Choreography 보상 |
| `delivery.created` | DeliveryService | OrderService, SlackService | Choreography |
| `delivery.creation.failed` | DeliveryService | HubService, OrderService | Choreography 보상 |
| `delivery.start`  | DeliveryService | HubService | Choreography |
| `cancel.delivery.command` | OrderService (Orch.) | DeliveryService | Orchestration |
| `delivery.cancelled.ack` | DeliveryService | OrderService (Orch.) | Orchestration |
| `restore.stock.command` | OrderService (Orch.) | HubService | Orchestration |
| `stock.restored.ack` | HubService | OrderService (Orch.) | Orchestration |
| `hub.stock.updated` | HubService | OrderService | 스냅샷 동기화 |
| `ai.deadline.calculated` | SlackService | DeliveryService | Choreography |
| `delivery.cancellation.failed` | DeliveryService  |  OrderService (Orch.)  |  Orchestration 보상  |
| `stock.restoration.failed` | HubService  |  OrderService (Orch.)  |  Orchestration 보상  |
- 비고
    - [x]  파티션 키 도입 제안 ✅
        - Kafka는 같은 파티션 내에서 프로듀서가 보낸 순서를 보장합니다.
        - 파티션이 여러 개인 환경에서 순서가 중요한 이벤트가 있다면, 비즈니스 식별자를 파티션 키로 지정해야 합니다.
        - 같은 키를 가진 메시지는 항상 같은 파티션으로 라우팅되므로, `orderId`를 파티션 키로 쓰면 동일 주문의 이벤트가 컨슈머 측에서 순서대로 처리됩니다.
        - 배달의민족에서 `배차완료 → 픽업완료 → 배달완료` 순서 보장에 파티션 키를 활용한 사례를 확인했고, 이 프로젝트의 주문 Saga 이벤트와 배송 생명주기 이벤트에도 동일하게 적용할 수 있을 것 같습니다.


            | 이벤트 그룹 | 파티션 키 | 이유 |
            | --- | --- | --- |
            | 주문 Saga 이벤트
            `order.created`, `stock.reserved`, `stock.reservation.failed`, `cancel.delivery.command`, `restore.stock.command`, `delivery.cancelled.ack`, `stock.restored.ack` | `orderId` | 동일 주문 이벤트가 같은 파티션으로 수렴되어 Saga 체인 순서 보장 |
            | 배송 생명주기 이벤트
            `delivery.created`, `delivery.creation.failed`, `ai.deadline.calculated`, `delivery.started` | `deliveryId` | 배송 단위 순서를 주문 Saga와 독립적으로 보장 |

---

## [Choreography Saga] 주문 생성

```mermaid
sequenceDiagram
    participant C as Client
    participant O as OrderService
    participant H as HubService
    participant D as DeliveryService
    participant S as SlackService

    C->>O: POST /orders
    Note over O: Step 1-1
    O->>H: order.created
    Note over H: Step 1-2
    H->>D: stock.reserved
    Note over D: Step 1-3
    D->>O: delivery.created
    D->>S: delivery.created
    Note over O: Step 1-4<br>PENDING → ACCEPTED
    Note over S: Step 1-5
    S->>D: ai.deadline.calculated
    Note over D: Step 1-6<br>finalDeadlineAt 저장
    D->>H: delivery.started
    Note over H: Step 1-7<br>reserved - N (실제 차감)
```

### [Step 1-1] `order.created` 발행

| 항목 | 내용 |
| --- | --- |
| 서비스 | **OrderService** |
| 진입점 | `OrderService.createOrder()` |
| 발행 토픽 | `order.created` |
| 파티션 키 | `orderId` |
| 처리 내용 | Order + OrderItem 생성(PENDING), `OrderCreatedEvent` 발행 |
| 이벤트 주요 필드 | `orderId`, `orderItems[]{productId, quantity, hubId}`, `requesterCompanyId`, `receiverCompanyId` |
| 다음 단계 | Step 1-2: HubService 재고 예약 |

### [Step 1-2] `order.created` 구독 → `stock.reserved` / `stock.reservation.failed` 발행

| 항목 | 내용 |
| --- | --- |
| 서비스 | **HubService** |
| 구독 토픽 | `order.created` |
| 처리 내용 | `orderItems` 순회하여 각 상품 재고 확인, 분산 락으로 `available` 차감 + `reserved` 증가 |
| 성공 시 발행 | `stock.reserved` (`orderId`, `destinationHubId`, `orderItems[]{productId, reservedQuantity, sourceHubId}`) |
| 실패 시 발행 | `stock.reservation.failed` (`orderId`, `productId`, `reason`) |
| 파티션 키 | `orderId` |
| 다음 단계 (성공) | Step 1-3: DeliveryService 배송 생성 |
| 다음 단계 (실패) | Step 2-1: OrderService 보상 취소 |
- 비고
    - HubService가 `order.created`를 수신하면 재고를 예약하는 것과는 별개로 실제 재고 차감을 어느 시점에서 할 지는 SA 문서 상에 명시되지 않았습니다.
        - [x]  `delivery.started` 토픽을 추가합니다.
            - `delivery.started` 수신 전 (reserved 보유 중): `reserved - N`, `available + N`
            - `delivery.started` 수신 후 (실제 차감 완료): `available + N`만 복구

### [Step 1-3] `stock.reserved` 구독 → `delivery.created` / `delivery.creation.failed` 발행

| 항목 | 내용 |
| --- | --- |
| 서비스 | **DeliveryService** |
| 구독 토픽 | `stock.reserved` |
| 처리 내용 | Hub Route 조회, `Delivery` + `DeliveryRoute` 생성, 배송 담당자 배정(미배정 시 null 허용) |
| 성공 시 발행 | `delivery.created` (`deliveryId`, `orderId`, `sourceHubId`, `destinationHubId`, `companyDeliveryManagerId`) |
| 실패 시 발행 | `delivery.creation.failed` (`orderId`, `deliveryId`, `reason`) |
| 파티션 키 | `deliveryId` (성공) / `orderId` (실패) |
| 다음 단계 (성공) | Step 1-4: OrderService ACCEPTED 전이, Step 1-5: SlackService AI 시한 계산 |
| 다음 단계 (실패) | Step 2-2: OrderService 보상 취소, Step 2-3: HubService 재고 복구 |
- 비고
    - DeliveryService는 출발 허브별로 배송을 1건하면 하나의 `orderId`에 대해 `delivery.created`가 N번 발행될 수 있는 구조인데 몇 번째 이벤트를 받았을 때 주문을 `ACCEPTED`로 전이해야 할까요? ✅
        - [ ]  첫 번째 `delivery.created` 수신 즉시 ACCEPTED ❌
            - 나머지 배송 생성이 실패해도 ACCEPTED 유지됩니다
        - [x]  `DeliveryCreatedEvent`에 `totalDeliveryCount` 필드 추가하고 수신 수가 해당 값에 도달하면 ACCEPTED ⭕
            - OrderService에 주문-배송 매핑 테이블(`p_order_delivery`) 추가해야 합니다
        - [ ]  DeliveryService가 N건 생성 완료 후 단일 집계 이벤트를 별도 발행 ❌
            - 토픽을 추가해야 하고 집계 로직도 구현해야 합니다
    - 현재 선형 구조인데 해당 로직처럼 여러 개의 배송 건이 합쳐지게 된다면 delivery쪽에서는 이벤트 유실 (정합성) 문제가 생길 수도 있을 것 같다는 생각이 듭니다. 때문에 outbox 패턴을 적용할까 고려중인데 학습비용때문에 고민입니다. 혹시 다른 도메인에서는 어떻게 처리하실 예정이신가요?
        - 두 가지 케이스가 있습니다.
            1. N건 배송 생성 도중 크래시: N건을 단일 트랜잭션으로 묶으면 DB에 일부만 저장되는 문제를 피할 수 있습니다.
            2. DB 저장은 됐는데 Kafka 발행 실패하는 경우: 해결하려면 DB에 `p_outbox` 테이블을 추가해야 합니다.
        - Hub와 Order에서도 같은 문제가 발생할 수 있기 때문에 따로따로 Outbox 패턴을 구현하는 것보다는 공통 모듈로 한꺼번에 적용하는 쪽이 더 효율적으로 보입니다.
        - 일단 기능 구현을 1차 목표로 **단일 트랜잭션 + Kafka 실패 시 retry + 컨슈머에서 처리된 `event_id`를 DB에 저장해 중복 처리를 방지하는 방향**으로 구현을 하고, 기능이 모두 구현된 이후에 Outbox 패턴 도입을 논의해 보면 좋을 것 같습니다.
        - Debezium을 사용한 CDC 인프라까지 구성하는 것은 오버스펙

### [Step 1-4] `delivery.created` 구독 → 주문 ACCEPTED 전이

| 항목 | 내용 |
| --- | --- |
| 서비스 | **OrderService** |
| 컨슈머 | `DeliveryCreatedConsumer` |
| 구독 토픽 | `delivery.created` |
| 위임 메서드 | `OrderService.acceptOrder()` |
| 처리 내용 | 주문 상태 PENDING → ACCEPTED, `deliveryId` 저장 |
| 멱등성 | PENDING이 아닌 경우 no-op |

### [Step 1-5] `delivery.created` 구독 → AI 발송 시한 계산 → `ai.deadline.calculated` 발행

| 항목 | 내용 |
| --- | --- |
| 서비스 | **SlackService (NotificationService)** |
| 구독 토픽 | `delivery.created` |
| 처리 내용 | AI API 호출로 `finalDeadlineAt` 산출, Slack 알림 발송 |
| 발행 토픽 | `ai.deadline.calculated` (`deliveryId`, `orderId`, `finalDeadlineAt`) |
| 파티션 키 | `deliveryId` |
| 다음 단계 | Step 1-6: DeliveryService `finalDeadlineAt` 저장 |

### [Step 1-6] `ai.deadline.calculated` 구독 → `finalDeadlineAt` 저장 → `delivery.started` 발행

| 항목 | 내용 |
| --- | --- |
| 서비스 | **DeliveryService** |
| 구독 토픽 | `ai.deadline.calculated` |
| 처리 내용 | 해당 Delivery에 `finalDeadlineAt` 저장 후 `delivery.started` 발행 |
| 발행 토픽 | `delivery.started` (`deliveryId`, `orderId`, `orderItems[]{productId, quantity}`) |
| 파티션 키 | `deliveryId` |
| 다음 단계 | Step 1-7: HubService 실제 재고 차감 |

### [Step 1-7] `delivery.started` 구독 → 실제 재고 차감

| 항목 | 내용 |
| --- | --- |
| 서비스 | **HubService** |
| 구독 토픽 | `delivery.started` |
| 처리 내용 | `orderItems` 순회하여 각 상품 `reserved - N`, `HubStockChangeType.DELIVERY_STARTED` 이력 기록 |
| 파티션 키 | `deliveryId` |
| 비고 | `HubStockChangeType`에 `DELIVERY_STARTED` 타입 추가 필요 |
- 비고
    - [x]  `delivery.start` 의 파티션 키를 무엇으로 해야 할까요? ✅
        - [ ]  `orderId` ❌
            - 재고 차감이 주문 흐름의 연장선이므로 주문 Saga 이벤트 그룹에 둬야 합니다.
            - 재고 처리까지 생각하면 주문에 이어지는 이벤트입니다.
        - [x]  `deliveryId` ⭕
            - 파티션 키는 '어떤 도메인의 이벤트인가'가 아니라 '같은 파티션 안에서 순서를 보장해야 하는 이벤트가 무엇인가'로 결정해야 합니다.
            - N개 허브 주문에서 `delivery.started`가 N번 발행될 때 `orderId`를 쓰면 같은 파티션에 몰려 불필요한 직렬화가 발생할 수 있습니다.


---

## [Choreography Saga] 주문 생성 보상 트랜잭션

```mermaid
sequenceDiagram
    participant H as HubService
    participant D as DeliveryService
    participant O as OrderService

    H->>O: stock.reservation.failed
    Note over O: Step 2-1<br>CANCELLED

    D->>O: delivery.creation.failed
    Note over O: Step 2-2<br>CANCELLED
    D->>H: delivery.creation.failed
    Note over H: Step 2-3<br>재고 복구 (CANCEL_RESTORE)
```

### [Step 2-1] `stock.reservation.failed` 구독 → 주문 CANCELLED

| 항목 | 내용 |
| --- | --- |
| 서비스 | **OrderService** |
| 컨슈머 | `StockReservationFailedConsumer` |
| 구독 토픽 | `stock.reservation.failed` |
| 위임 메서드 | `OrderService.cancelOrderByCompensation()` |
| 처리 내용 | 주문 즉시 CANCELLED, `cancelReason` = 실패 사유 |
| 멱등성 | 이미 CANCELLED인 경우 no-op |

### [Step 2-2] `delivery.creation.failed` 구독 → 주문 CANCELLED

| 항목 | 내용 |
| --- | --- |
| 서비스 | **OrderService** |
| 컨슈머 | `DeliveryCreationFailedConsumer` |
| 구독 토픽 | `delivery.creation.failed` |
| 위임 메서드 | `OrderService.cancelOrderByCompensation()` |
| 처리 내용 | 주문 즉시 CANCELLED, `cancelReason` = 실패 사유 |
| 멱등성 | 이미 CANCELLED인 경우 no-op |

### [Step 2-3] `delivery.creation.failed` 구독 → 재고 예약 복구

| 항목 | 내용 |
| --- | --- |
| 서비스 | **HubService** |
| 구독 토픽 | `delivery.creation.failed` |
| 처리 내용 | `releaseReservation()` 호출로 `reserved` 차감 + `available` 복구, `HubStockChangeType.CANCEL_RESTORE` 이력 기록 |
| 비고 | Step 2-2와 동일 토픽을 독립적으로 구독 |

---

## [Orchestration Saga] 주문 취소

```mermaid
sequenceDiagram
    participant C as Client
    participant O as OrderService
    participant Orch as CancelOrderOrchestrator
    participant D as DeliveryService
    participant H as HubService

    C->>O: DELETE /orders/{orderId}
    Note over O: 권한 검사 + 주문 조회
    O->>Orch: start(order, userId, cancelReason)
    Note over Orch: Step 3-1 / CANCELLING 전이
    Orch->>D: cancel.delivery.command
    Note over D: Step 3-2 / 배송 취소
    D->>Orch: delivery.cancelled.ack
    Note over Orch: Step 3-3
    Orch->>H: restore.stock.command
    Note over H: Step 3-4 / 재고 복구
    H->>Orch: stock.restored.ack
    Note over Orch: Step 3-5 / CANCELLED 확정
```

### `CancelOrderOrchestrator`

| 항목 | 내용 |
| --- | --- |
| 역할 | Orchestration Saga 중앙 조율자 |
| 의존성 | `OrderRepository`, `KafkaTemplate` |
| 메서드 | `start(Order, UUID, String)` / `onDeliveryCancelled(UUID)` / `onStockRestored(UUID)` |
| 트랜잭션 | 메서드별 `@Transactional` (각 단계 독립 트랜잭션) |
| 멱등성 전략 | 주문 없음 → warn 후 no-op / CANCELLING 아님 → warn 후 no-op |
| 커맨드 식별 | 발행 시 `eventId = UUID.randomUUID()` 부여 |
| 파티션 키 | 모든 발행에 `orderId` 사용 → 동일 주문의 명령 순서 보장 |
- 비고
    - 설계 초기에는 Orchestration Saga 패턴이 주문 취소 한 군데에만 적용되기 때문에 Orchestrator 컴포넌트 추가 없이 OrderService에서 조율자 역할을 하는 것으로 되어 있었습니다.
    - 이후 OrderService가 비대해지자 비즈니스 책임을 명확히 하기 위해 Orchestrator를 따로 구현하였습니다.

### [Step 3-1] `cancel.delivery.command` 발행

| 항목 | 내용 |
| --- | --- |
| 서비스 | **OrderService** |
| 담당 컴포넌트 | `CancelOrderOrchestrator.start()` |
| 진입점 | `OrderService.cancelOrder()` → `CancelOrderOrchestrator.start()` |
| 발행 토픽 | `cancel.delivery.command` |
| 파티션 키 | `orderId` |
| 처리 내용 | 주문 상태 → CANCELLING, `cancelledBy` 및 `cancelReason` 기록, 커맨드 발행 |
| 이벤트 주요 필드 | `orderId`, `deliveryId` |
| 전이 가능 상태 | PENDING, ACCEPTED (IN_DELIVERY 이상은 `ORDER_NOT_CANCELLABLE` 예외) |
| 권한 검사 | `OrderService.cancelOrder()`에서 MASTER/HUB_MANAGER 여부 및 허브 담당 검사 후 위임 |
| 다음 단계 | Step 3-2: DeliveryService 배송 취소 |

### [Step 3-2] `cancel.delivery.command` 구독 → `delivery.cancelled.ack` 발행

| 항목 | 내용 |
| --- | --- |
| 서비스 | **DeliveryService** |
| 구독 토픽 | `cancel.delivery.command` |
| 처리 내용 | 배송 상태 확인 후 취소 처리 (`PENDING`/`ACCEPTED` → 취소 가능, `IN_TRANSIT` 이상 → 취소 불가) |
| 성공 시 발행 | `delivery.cancelled.ack` (`deliveryId`, `orderId`) |
| 실패 시 발행 | `delivery.cancellation.failed` (`orderId`, `deliveryId`, `reason`) |
| 파티션 키 | `orderId` |
| 다음 단계 (성공) | Step 3-3: OrderService `restore.stock.command` 발행 |
| 다음 단계 (실패) | Step 4-1: OrderService CANCELLING → 이전 상태 복구 |

### [Step 3-3] `delivery.cancelled.ack` 구독 → `restore.stock.command` 발행

| 항목 | 내용 |
| --- | --- |
| 서비스 | **OrderService** |
| 담당 컴포넌트 | `CancelOrderOrchestrator.onDeliveryCancelled()` |
| 컨슈머 | `DeliveryCancelledAckConsumer` |
| 구독 토픽 | `delivery.cancelled.ack` |
| 위임 메서드 | `OrderService.handleDeliveryCancelled()` → `CancelOrderOrchestrator.onDeliveryCancelled()` |
| 발행 토픽 | `restore.stock.command` |
| 파티션 키 | `orderId` |
| 처리 내용 | `OrderItem` 목록으로 복구 대상 상품·수량 구성 후 재고 복구 커맨드 발행 |
| 이벤트 주요 필드 | `orderId`, `orderItems[]{productId, quantity}` |
| 멱등성 | CANCELLING이 아닌 경우 no-op |
| 다음 단계 | Step 3-4: HubService 재고 복구 |

### [Step 3-4] `restore.stock.command` 구독 → `stock.restored.ack` 발행

| 항목 | 내용 |
| --- | --- |
| 서비스 | **HubService** |
| 구독 토픽 | `restore.stock.command` |
| 처리 내용 | `orderItems` 순회하여 각 상품 `reserved` 차감 + `available` 복구, `HubStockChangeType.CANCEL_RESTORE` 이력 기록 |
| 성공 시 발행 | `stock.restored.ack` (`orderId`) |
| 실패 시 발행 | `stock.restoration.failed` (`orderId`, `reason`) |
| 파티션 키 | `orderId` |
| 다음 단계 (성공) | Step 3-5: OrderService CANCELLED 확정 |
| 다음 단계 (실패) | Step 4-2: OrderService `restore.stock.command` 재발행 |

### [Step 3-5] `stock.restored.ack` 구독 → 주문 CANCELLED 확정

| 항목 | 내용 |
| --- | --- |
| 서비스 | **OrderService** |
| 담당 컴포넌트 | `CancelOrderOrchestrator.onStockRestored()` |
| 컨슈머 | `StockRestoredAckConsumer` |
| 구독 토픽 | `stock.restored.ack` |
| 위임 메서드 | `OrderService.confirmOrderCancelled()` → `CancelOrderOrchestrator.onStockRestored()` |
| 처리 내용 | 주문 상태 CANCELLING → CANCELLED, `cancelledAt` 기록 |
| 멱등성 | CANCELLING이 아닌 경우 no-op |
| 비고 | `cancelledBy` 및 `cancelReason`은 Step 3-1에서 이미 저장되었으므로 기록하지 않음 |

---

## [Orchestration Saga] 주문 취소 보상 트랜잭션

취소 흐름에서 외부 서비스가 실패할 수 있는 단계는 두 곳입니다.

| 실패 단계 | 실패 시 이미 완료된 것 | 보상 방향 |
| --- | --- | --- |
| Step 3-2: 배송 취소 거부 | 없음 | 주문 `CANCELLING` → 이전 상태(`PENDING`/`ACCEPTED`) 복구 |
| Step 3-4: 재고 복구 실패 | 배송 취소 완료 | `restore.stock.command` 재발행 (재시도) |

### Step 3-2 실패 (배송 취소 거부)

```mermaid
sequenceDiagram
    participant Orch as CancelOrderOrchestrator
    participant D as DeliveryService
    participant O as OrderService

    Orch->>D: cancel.delivery.command
    Note over D: 배송 취소 불가 (IN_TRANSIT 등)
    D->>O: delivery.cancellation.failed
    Note over O: DeliveryCancellationFailedConsumer
    O->>Orch: onDeliveryCancellationFailed(orderId)
    Note over Orch: Step 4-1 / CANCELLING → PENDING or ACCEPTED 복구
```

### [Step 4-1] `delivery.cancellation.failed` 구독 → 주문 이전 상태 복구

| 항목 | 내용 |
| --- | --- |
| 서비스 | **OrderService** |
| 담당 컴포넌트 | `CancelOrderOrchestrator.onDeliveryCancellationFailed()` |
| 컨슈머 | `DeliveryCancellationFailedConsumer` |
| 구독 토픽 | `delivery.cancellation.failed` |
| 처리 내용 | `CANCELLING` → 이전 상태 복구, `cancelledBy` 및 `cancelReason` 초기화 |
| 이전 상태 판별 | `deliveryId == null ? PENDING : ACCEPTED`  |
| 멱등성 | CANCELLING이 아닌 경우 no-op |
| 비고 | 이 시점에서 재고·배송 모두 변경된 것이 없으므로 주문 상태만 복구하면 됨 |

### Step 3-4 실패 (재고 복구 실패)

```mermaid
sequenceDiagram
    participant Orch as CancelOrderOrchestrator
    participant H as HubService
    participant O as OrderService

    Orch->>H: restore.stock.command
    Note over H: 재고 복구 실패
    H->>O: stock.restoration.failed
    Note over O: StockRestorationFailedConsumer
    O->>Orch: onStockRestorationFailed(orderId)
    Note over Orch: Step 4-2 / restore.stock.command 재발행
    Orch->>H: restore.stock.command (재시도)
```

### [Step 4-2] `stock.restoration.failed` 구독 → `restore.stock.command` 재발행

| 항목 | 내용 |
| --- | --- |
| 서비스 | **OrderService** |
| 담당 컴포넌트 | `CancelOrderOrchestrator.onStockRestorationFailed()` |
| 컨슈머 | `StockRestorationFailedConsumer` |
| 구독 토픽 | `stock.restoration.failed` |
| 처리 내용 | `restore.stock.command` 재발행 |
| 멱등성 | CANCELLING이 아닌 경우 no-op |
| 비고 |   • 재고 복구는 `reserved` 차감 + `available` 증가로 멱등 연산이므로 재시도 안전
  • 이 시점에서 배송 취소는 이미 완료되어 원복 불가 |

---

## 주문 상태 전이

```mermaid
stateDiagram-v2
    [*] --> PENDING : 주문 생성
    PENDING --> ACCEPTED : delivery.created (Step 1-4)
    PENDING --> CANCELLED : stock.reservation.failed (Step 2-1)
    PENDING --> CANCELLED : delivery.creation.failed (Step 2-2)
    PENDING --> CANCELLING : 취소 요청 (Step 3-1)
    ACCEPTED --> IN_DELIVERY : 배송 시작 (DeliveryService)
    ACCEPTED --> CANCELLED : delivery.creation.failed (Step 2-2)
    ACCEPTED --> CANCELLING : 취소 요청 (Step 3-1)
    CANCELLING --> CANCELLED : stock.restored.ack (Step 3-5)
    IN_DELIVERY --> COMPLETED : 배송 완료
    CANCELLED --> [*]
    COMPLETED --> [*]
```

| 현재 상태 | 전이 후 상태 | 조건 |
| --- | --- | --- |
| `PENDING` | `ACCEPTED` | `delivery.created` 수신 (Step 1-4) |
| `PENDING` | `CANCELLED` | `stock.reservation.failed` 수신 (Step 2-1) |
| `PENDING` | `CANCELLED` | `delivery.creation.failed` 수신 (Step 2-2) |
| `PENDING` | `CANCELLING` | 취소 요청 (MASTER/HUB_MANAGER, Step 3-1) |
| `ACCEPTED` | `IN_DELIVERY` | 배송 시작 (DeliveryService 외부 이벤트) |
| `ACCEPTED` | `CANCELLED` | `delivery.creation.failed` 수신 (Step 2-2) |
| `ACCEPTED` | `CANCELLING` | 취소 요청 (MASTER/HUB_MANAGER, Step 3-1) |
| `CANCELLING` | `CANCELLED` | `stock.restored.ack` 수신 (Step 3-5) |
| `IN_DELIVERY` 이상 | 취소 불가 | `ORDER_NOT_CANCELLABLE` 예외 |

※ `CANCELLING` 중에는 수정 불가