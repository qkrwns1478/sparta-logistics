package com.sparta.logistics.hub.hub.repository;

import com.sparta.logistics.hub.hub.entity.Hub;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface HubRepository extends JpaRepository<Hub, UUID> {

    boolean existsByName(String name);
}
