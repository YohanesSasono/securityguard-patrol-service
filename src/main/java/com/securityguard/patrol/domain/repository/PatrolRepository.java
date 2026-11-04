package com.securityguard.patrol.domain.repository;

import com.securityguard.patrol.domain.Patrol;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PatrolRepository extends JpaRepository<Patrol, UUID> {
    Page<Patrol> findBySiteCompanyId(UUID companyId, Pageable pageable);
    Optional<Patrol> findByIdAndSiteCompanyId(UUID id, UUID companyId);
}
