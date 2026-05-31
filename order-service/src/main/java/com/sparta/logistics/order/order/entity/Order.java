package com.sparta.logistics.order.order.entity;

import com.sparta.logistics.common.domain.BaseEntity;
import com.sparta.logistics.common.exception.BusinessException;
import com.sparta.logistics.order.exception.OrderErrorCode;
import com.sparta.logistics.order.order.enums.OrderStatus;
import com.sparta.logistics.order.orderitem.entity.OrderItem;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "p_order")
@SQLRestriction("deleted_at IS NULL")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseEntity {

    @Id
    private UUID id;

    @Column(nullable = false, name = "requester_company_id")
    private UUID requesterCompanyId;

    @Column(nullable = false, name = "receiver_company_id")
    private UUID receiverCompanyId;

    @Column(nullable = false, name = "requester_user_id")
    private UUID requesterUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @Column(nullable = false, name = "total_amount")
    private Long totalAmount;

    @Column(nullable = false, name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "request_memo")
    private String requestMemo;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancel_reason")
    private String cancelReason;

    /**
     * 배송 ID
     * delivery.created 이벤트 수신 후 저장
     * Orchestration Saga 취소 시 cancel.delivery.command 페이로드로 사용됨
     * */
    @Column(name = "delivery_id")
    private UUID deliveryId;

    @Version
    @Column(nullable = false)
    private Long version;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    public static Order create(UUID requesterCompanyId, UUID receiverCompanyId, UUID requesterUserId, LocalDateTime dueDate, String requestMemo) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.requesterCompanyId = requesterCompanyId;
        order.receiverCompanyId = receiverCompanyId;
        order.requesterUserId = requesterUserId;
        order.status = OrderStatus.PENDING;
        order.totalAmount = 0L;
        order.dueDate = dueDate;
        order.requestMemo = requestMemo;
        return order;
    }

    public void addOrderItem(OrderItem item) {
        orderItems.add(item);
    }

    public void calculateTotalAmount() {
        this.totalAmount = orderItems.stream()
                .mapToLong(OrderItem::getSubTotal)
                .sum();
    }

    public void update(LocalDateTime dueDate, String requestMemo) {
        if (dueDate != null) this.dueDate = dueDate;
        if (requestMemo != null) this.requestMemo = requestMemo;
    }

    public void cancel(UUID cancelledBy, String cancelReason) {
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        this.cancelledBy = cancelledBy;
        this.cancelReason = cancelReason;
    }

    /**
     * Orchestration Saga Step 1: 취소 진행 중 상태로 전이
     * cancel.delivery.command 발행 직전에 호출함
     * 실제 CANCELLED 확정은 stock.restored.ack 수신 후 confirmCancelled()가 담당함
     * */
    public void startCancelling(UUID cancelledBy, String cancelReason) {
        if (!canTransitionTo(OrderStatus.CANCELLING)) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_CANCELLABLE);
        }
        this.status = OrderStatus.CANCELLING;
        this.cancelledBy = cancelledBy;
        this.cancelReason = cancelReason;
    }

    /**
     * Orchestration Saga 보상 Step 4-1: delivery.cancellation.failed 수신 후 CANCELLING 이전 상태로 복구
     * <p>
     * 이전 상태는 deliveryId 유무로 추론함
     *   - deliveryId == null → PENDING (delivery.created 수신 전에 취소 요청)
     *   - deliveryId != null → ACCEPTED (delivery.created 수신 후에 취소 요청)
     * CANCELLING 상태가 아니면 ORDER_INVALID_STATE_TRANSITION 예외를 던짐
     */
    public void revertCancelling(OrderStatus previous) {
        if (this.status != OrderStatus.CANCELLING) {
            throw new BusinessException(OrderErrorCode.ORDER_INVALID_STATE_TRANSITION);
        }
        this.status = previous;
        this.cancelledBy = null;
        this.cancelReason = null;
    }

    /** Orchestration Saga Step 3-5: stock.restored.ack 수신 후 CANCELLED 확정 **/
    public void confirmCancelled() {
        if (this.status != OrderStatus.CANCELLING) {
            throw new BusinessException(OrderErrorCode.ORDER_INVALID_STATE_TRANSITION);
        }
        this.status = OrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
    }

    /** delivery.created 이벤트 수신 시 ACCEPTED로 전이 **/
    public void accept() {
        if (!canTransitionTo(OrderStatus.ACCEPTED)) {
            throw new BusinessException(OrderErrorCode.ORDER_INVALID_STATE_TRANSITION);
        }
        this.status = OrderStatus.ACCEPTED;
    }

    /**
     * delivery.created 수신 시 배송 ID를 저장함
     * Orchestration Saga 취소 명령 시 deliveryId가 필요함
     * */
    public void linkDelivery(UUID deliveryId) {
        this.deliveryId = deliveryId;
    }

    public boolean isModifiable() {
        return this.status != OrderStatus.CANCELLED
                && this.status != OrderStatus.CANCELLING // 취소 진행 중도 수정 불가
                && this.status != OrderStatus.COMPLETED
                && this.status != OrderStatus.IN_DELIVERY;
    }

    public boolean isDeletable() {
        return this.status == OrderStatus.CANCELLED || this.status == OrderStatus.COMPLETED;
    }

    public void delete(UUID deletedBy) {
        softDelete(deletedBy);
        orderItems.forEach(item -> item.softDelete(deletedBy));
    }

    // 상태 전이 유효성 테이블
    private boolean canTransitionTo(OrderStatus next) {
        return switch (this.status) {
            case PENDING -> next == OrderStatus.ACCEPTED || next == OrderStatus.CANCELLING;
            case ACCEPTED -> next == OrderStatus.IN_DELIVERY || next == OrderStatus.CANCELLING;
            // IN_DELIVERY 이상은 취소 불가 (cancel.delivery.command 발행 시 예외)
            default -> false;
        };
    }
}
