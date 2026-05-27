## Kafka 메시지 DTO

```text
common/src/main/java/com/sparta/logistics/common/kafka/
├── event/
│   ├── OrderCreatedEvent.java                # order.created
│   ├── StockReservedEvent.java               # stock.reserved
│   ├── StockReservationFailedEvent.java      # stock.reservation.failed
│   ├── DeliveryCreatedEvent.java             # delivery.created
│   ├── DeliveryCreationFailedEvent.java      # delivery.creation.failed
│   ├── DeliveryStartedEvent.java             # delivery.started
│   ├── CancelDeliveryCommand.java            # cancel.delivery.command
│   ├── DeliveryCancelledAckEvent.java        # delivery.cancelled.ack
│   ├── DeliveryCancellationFailedEvent.java  # delivery.cancellation.failed
│   ├── RestoreStockCommand.java              # restore.stock.command
│   ├── StockRestoredAckEvent.java            # stock.restored.ack
│   ├── StockRestorationFailedEvent.java      # stock.restoration.failed
│   ├── HubStockUpdatedEvent.java             # hub.stock.updated
│   ├── AiDeadlineCalculatedEvent.java        # ai.deadline.calculated
│   ├── RestoreStockItemPayload.java          # RestoreStockCommand 내부 페이로드
│   └── DeliveryOrderItemPayload.java         # DeliveryStartedEvent 내부 페이로드
└── KafkaTopics.java                          # 토픽명 상수 모음
```

### 공통 필드

모든 이벤트/커맨드는 `eventId(UUID)`를 첫 번째 필드로 가진다.
컨슈머에서 중복 소비 방지를 위해 사용하며, 발행 측에서 `UUID.randomUUID()`로 생성한다.

### 주요 필드 요약

| DTO | 핵심 필드 |
|---|---|
| `OrderCreatedEvent` | `eventId`, `orderId`, `orderItems[]{orderItemId, productId, quantity, hubId}`, `requesterCompanyId`, `receiverCompanyId` |
| `StockReservedEvent` | `eventId`, `orderId`, `destinationHubId`, `orderItems[]{productId, reservedQuantity, sourceHubId}` |
| `StockReservationFailedEvent` | `eventId`, `orderId`, `productId`, `reason` |
| `DeliveryCreatedEvent` | `eventId`, `deliveryId`, `orderId`, `sourceHubId`, `destinationHubId`, `companyDeliveryManagerId`, `totalDeliveryCount` |
| `DeliveryCreationFailedEvent` | `eventId`, `orderId`, `deliveryId`, `reason`, `itemsToRestore[]{orderItemId, productId, hubId, quantity}` |
| `DeliveryStartedEvent` | `eventId`, `deliveryId`, `orderId`, `orderItems[]{orderItemId, productId, hubId, quantity}` |
| `CancelDeliveryCommand` | `eventId`, `orderId`, `deliveryId` |
| `DeliveryCancelledAckEvent` | `eventId`, `deliveryId`, `orderId` |
| `DeliveryCancellationFailedEvent` | `eventId`, `orderId`, `deliveryId`, `reason` |
| `RestoreStockCommand` | `eventId`, `orderId`, `orderItems[]{orderItemId, productId, hubId, quantity}` |
| `StockRestoredAckEvent` | `eventId`, `orderId` |
| `StockRestorationFailedEvent` | `eventId`, `orderId`, `reason` |
| `HubStockUpdatedEvent` | `eventId`, `productId`, `hubId`, `available`, `hubStockVersion` |
| `AiDeadlineCalculatedEvent` | `eventId`, `deliveryId`, `orderId`, `finalDeadlineAt` |
