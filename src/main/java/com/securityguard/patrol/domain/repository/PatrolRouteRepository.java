package com.securityguard.patrol.domain.repository;

import com.securityguard.patrol.domain.PatrolRoute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PatrolRouteRepository extends JpaRepository<PatrolRoute, UUID> {
    Page<PatrolRoute> findBySiteIdIn(Collection<UUID> siteIds, Pageable pageable);
    Optional<PatrolRoute> findByIdAndSiteIdIn(UUID id, Collection<UUID> siteIds);
}
