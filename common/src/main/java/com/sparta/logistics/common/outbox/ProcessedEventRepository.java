package com.sparta.logistics.common.outbox;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, UUID> {

    void deleteByProcessedAtBefore(LocalDateTime threshold);
}
