package com.securityguard.patrol.application.port.in;

import com.securityguard.patrol.application.PatrolRouteService;
import com.securityguard.patrol.domain.PatrolRoute;
import com.securityguard.patrol.domain.PatrolRoutePoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.UUID;

public interface PatrolRouteUseCase {
    Page<PatrolRoute> list(UUID companyId, Pageable pageable);
    PatrolRoute getById(UUID id, UUID companyId);
    List<PatrolRoutePoint> listRoutePoints(UUID routeId, UUID companyId);
    PatrolRoute create(UUID companyId, PatrolRouteService.CreateRouteCommand command);
    PatrolRoute update(UUID id, UUID companyId, PatrolRouteService.UpdateRouteCommand command);
    void deactivate(UUID id, UUID companyId);
    PatrolRoutePoint addPoint(UUID routeId, UUID companyId, PatrolRouteService.AddRoutePointCommand command);
    List<PatrolRoutePoint> reorderPoints(UUID routeId, UUID companyId, List<PatrolRouteService.AddRoutePointCommand> points);
    void removePoint(UUID routeId, UUID pointId, UUID companyId);
}
