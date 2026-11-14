package com.securityguard.patrol.domain.repository;

import com.securityguard.patrol.domain.PatrolPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface PatrolPointRepository extends JpaRepository<PatrolPoint, UUID> {
    // Tenancy: sites live in identity-service, so "belongs to the company" == "siteId IN the company's site ids".
    Page<PatrolPoint> findBySiteId(UUID siteId, Pageable pageable);
    Page<PatrolPoint> findBySiteIdIn(Collection<UUID> siteIds, Pageable pageable);
    Optional<PatrolPoint> findByIdAndSiteIdIn(UUID id, Collection<UUID> siteIds);
    boolean existsByNfcUid(String nfcUid);
}
