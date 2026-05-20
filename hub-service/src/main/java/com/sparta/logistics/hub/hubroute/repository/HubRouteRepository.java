package com.sparta.logistics.hub.hubroute.repository;

import com.sparta.logistics.hub.hub.entity.Hub;
import com.sparta.logistics.hub.hubroute.entity.HubRoute;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface HubRouteRepository extends JpaRepository<HubRoute, UUID> {

    boolean existsBySourceHubAndDestinationHubAndDeletedAtIsNull(Hub sourceHub, Hub destinationHub);

    Optional<HubRoute> findByIdAndDeletedAtIsNull(UUID hubRouteId);
}
