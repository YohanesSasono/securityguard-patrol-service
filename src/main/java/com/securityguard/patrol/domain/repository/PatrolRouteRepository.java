package com.securityguard.patrol.domain.repository;

import com.securityguard.patrol.domain.PatrolRoute;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatrolRouteRepository extends JpaRepository<PatrolRoute, UUID> {
    Page<PatrolRoute> findBySiteCompanyId(UUID companyId, Pageable pageable);
    Optional<PatrolRoute> findByIdAndSiteCompanyId(UUID id, UUID companyId);
}
