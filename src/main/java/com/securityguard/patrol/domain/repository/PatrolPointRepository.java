package com.securityguard.patrol.domain.repository;

import com.securityguard.patrol.domain.PatrolPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatrolPointRepository extends JpaRepository<PatrolPoint, UUID> {
    Page<PatrolPoint> findBySiteIdAndSiteCompanyId(UUID siteId, UUID companyId, Pageable pageable);
    Page<PatrolPoint> findBySiteCompanyId(UUID companyId, Pageable pageable);
    Optional<PatrolPoint> findByIdAndSiteCompanyId(UUID id, UUID companyId);
    boolean existsByNfcUid(String nfcUid);
}
