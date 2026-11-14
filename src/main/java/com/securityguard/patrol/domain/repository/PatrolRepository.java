package com.securityguard.patrol.domain.repository;

import com.securityguard.patrol.domain.Patrol;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PatrolRepository extends JpaRepository<Patrol, UUID> {
    Page<Patrol> findBySiteIdIn(Collection<UUID> siteIds, Pageable pageable);
    Optional<Patrol> findByIdAndSiteIdIn(UUID id, Collection<UUID> siteIds);
}
