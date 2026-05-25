package com.sparta.logistics.hub.client;

import com.sparta.logistics.hub.client.response.CompanyResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.UUID;

@FeignClient(name = "company-service")
public interface CompanyClient {

    @GetMapping("/api/v1/companies/{companyId}")
    CompanyResponse getCompany(@PathVariable UUID companyId);
}
