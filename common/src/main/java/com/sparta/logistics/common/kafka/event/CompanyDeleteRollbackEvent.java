package com.sparta.logistics.common.kafka.event;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class CompanyDeleteRollbackEvent {
    private UUID companyId;
    private String reason;
}
