package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDeletedEvent {
    private UUID companyId;
    private UUID deletedBy;
    private LocalDateTime deletedAt;
}
