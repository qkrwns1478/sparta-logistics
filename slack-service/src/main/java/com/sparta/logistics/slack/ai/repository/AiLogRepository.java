package com.sparta.logistics.slack.ai.repository;

import com.sparta.logistics.slack.ai.entity.AiLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AiLogRepository extends JpaRepository<AiLog, UUID> {
}
