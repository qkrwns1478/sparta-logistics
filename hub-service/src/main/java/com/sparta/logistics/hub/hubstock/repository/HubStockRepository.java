package com.sparta.logistics.hub.hubstock.repository;

import com.sparta.logistics.hub.hub.entity.Hub;
import com.sparta.logistics.hub.hubstock.entity.HubStock;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HubStockRepository extends JpaRepository<HubStock, UUID> {

    boolean existsByHubAndProductId(Hub hub, UUID productId);
}
