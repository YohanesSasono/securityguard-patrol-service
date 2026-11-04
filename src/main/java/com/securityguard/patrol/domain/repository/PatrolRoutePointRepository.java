package com.securityguard.patrol.domain.repository;

import com.securityguard.patrol.domain.PatrolRoutePoint;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PatrolRoutePointRepository extends JpaRepository<PatrolRoutePoint, UUID> {
    List<PatrolRoutePoint> findByRouteIdOrderBySequenceAsc(UUID routeId);
    Optional<PatrolRoutePoint> findByRouteIdAndPointId(UUID routeId, UUID pointId);
    boolean existsByRouteIdAndSequence(UUID routeId, Integer sequence);
    void deleteByRouteIdAndPointId(UUID routeId, UUID pointId);
    long countByRouteIdAndRequiredTrue(UUID routeId);
}
