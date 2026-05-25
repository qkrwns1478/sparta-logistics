## Kafka 메시지 DTO

```text
common/src/main/java/com/sparta/logistics/common/kafka/
├── event/
│   ├── OrderCreatedEvent.java           # order.created
│   ├── StockReservedEvent.java          # stock.reserved
│   ├── StockReservationFailedEvent.java # stock.reservation.failed
│   ├── DeliveryCreatedEvent.java        # delivery.created
│   ├── DeliveryCreationFailedEvent.java # delivery.creation.failed
│   ├── CancelDeliveryCommand.java       # cancel.delivery.command
│   ├── DeliveryCancelledAckEvent.java   # delivery.cancelled.ack
│   ├── RestoreStockCommand.java         # restore.stock.command
│   ├── StockRestoredAckEvent.java       # stock.restored.ack
│   ├── HubStockUpdatedEvent.java        # hub.stock.updated
│   └── AiDeadlineCalculatedEvent.java   # ai.deadline.calculated
└── KafkaTopics.java                     # 토픽명 상수 모음
```

### 주요 필드 요약

| DTO | 핵심 필드 |
|---|---|
| `OrderCreatedEvent` | `orderId`, `orderItems[]{productId, quantity, hubId}`, `requesterCompanyId` |
| `StockReservedEvent` | `orderId`, `sourceHubId`, `destinationHubId`, `orderItems[]{productId, reservedQuantity}` |
| `StockReservationFailedEvent` | `orderId`, `productId`, `reason` |
| `DeliveryCreatedEvent` | `deliveryId`, `orderId`, `sourceHubId`, `destinationHubId`, `companyDeliveryManagerId` |
| `DeliveryCreationFailedEvent` | `orderId`, `deliveryId`, `reason` |
| `CancelDeliveryCommand` | `orderId`, `deliveryId` |
| `DeliveryCancelledAckEvent` | `deliveryId`, `orderId` |
| `RestoreStockCommand` | `orderId`, `orderItems[]{productId, quantity}` |
| `StockRestoredAckEvent` | `orderId` |
| `HubStockUpdatedEvent` | `productId`, `hubId`, `available`, `hubStockVersion` |
| `AiDeadlineCalculatedEvent` | `deliveryId`, `orderId`, `finalDeadlineAt` |