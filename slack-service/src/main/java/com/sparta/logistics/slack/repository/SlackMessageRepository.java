package com.sparta.logistics.slack.repository;

import com.sparta.logistics.slack.entity.SlackMessage;
import com.sparta.logistics.slack.enums.MessageType;
import com.sparta.logistics.slack.enums.RelatedType;
import com.sparta.logistics.slack.enums.SlackMessageStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface SlackMessageRepository extends JpaRepository<SlackMessage, UUID> {

    Optional<SlackMessage> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            SELECT sm FROM SlackMessage sm
            WHERE sm.deletedAt IS NULL
              AND (:receiverSlackId IS NULL OR sm.receiverSlackId = :receiverSlackId)
              AND (:messageType IS NULL OR sm.messageType = :messageType)
              AND (:status IS NULL OR sm.status = :status)
              AND (:relatedType IS NULL OR sm.relatedType = :relatedType)
              AND (:relatedId IS NULL OR sm.relatedId = :relatedId)
            """)
    Page<SlackMessage> search(
            @Param("receiverSlackId") String receiverSlackId,
            @Param("messageType") MessageType messageType,
            @Param("status") SlackMessageStatus status,
            @Param("relatedType") RelatedType relatedType,
            @Param("relatedId") UUID relatedId,
            Pageable pageable
    );
}
