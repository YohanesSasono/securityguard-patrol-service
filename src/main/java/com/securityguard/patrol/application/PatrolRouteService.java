package com.securityguard.patrol.application;

import com.securityguard.patrol.domain.*;
import com.securityguard.patrol.domain.repository.PatrolPointRepository;
import com.securityguard.patrol.application.port.in.PatrolRouteUseCase;
import com.securityguard.patrol.domain.repository.PatrolRoutePointRepository;
import com.securityguard.patrol.domain.repository.PatrolRouteRepository;
import com.securityguard.patrol.application.port.out.SiteLookupPort;
import com.securityguard.shared.exception.BusinessValidationException;
import com.securityguard.shared.exception.ConflictException;
import com.securityguard.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Owns the "route points must belong to the same site as the route" rule
 * (API_SPEC_V2.txt / DB_SCHEMA_V2.txt) and route-sequence integrity
 * (uniqueness enforced by the DB too - see V3 migration's
 * uq_patrol_route_points_route_sequence - this service pre-checks for a
 * clean error message, the constraint is the real guarantee).
 */
@Service
@Transactional
public class PatrolRouteService implements PatrolRouteUseCase {

    private final PatrolRouteRepository patrolRouteRepository;
    private final PatrolRoutePointRepository patrolRoutePointRepository;
    private final PatrolPointRepository patrolPointRepository;
    private final SiteLookupPort siteLookup;

    public PatrolRouteService(PatrolRouteRepository patrolRouteRepository,
                               PatrolRoutePointRepository patrolRoutePointRepository,
                               PatrolPointRepository patrolPointRepository,
                               SiteLookupPort siteLookup) {
        this.patrolRouteRepository = patrolRouteRepository;
        this.patrolRoutePointRepository = patrolRoutePointRepository;
        this.patrolPointRepository = patrolPointRepository;
        this.siteLookup = siteLookup;
    }

    public record CreateRouteCommand(UUID siteId, String name, RouteType type) {
    }

    public record UpdateRouteCommand(String name, RouteType type, Boolean active) {
    }

    public record AddRoutePointCommand(UUID pointId, int sequence, boolean required) {
    }

    @Transactional(readOnly = true)
    public Page<PatrolRoute> list(UUID companyId, Pageable pageable) {
        return patrolRouteRepository.findBySiteIdIn(siteLookup.siteIdsOf(companyId), pageable);
    }

    @Transactional(readOnly = true)
    public PatrolRoute getById(UUID id, UUID companyId) {
        return patrolRouteRepository.findByIdAndSiteIdIn(id, siteLookup.siteIdsOf(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Patrol route not found"));
    }

    @Transactional(readOnly = true)
    public List<PatrolRoutePoint> listRoutePoints(UUID routeId, UUID companyId) {
        getById(routeId, companyId); // tenant check
        return patrolRoutePointRepository.findByRouteIdOrderBySequenceAsc(routeId);
    }

    public PatrolRoute create(UUID companyId, CreateRouteCommand command) {
        siteLookup.requireSite(companyId, command.siteId());

        PatrolRoute route = new PatrolRoute();
        route.setSiteId(command.siteId());
        route.setName(command.name());
        route.setType(command.type());
        route.setActive(true);
        return patrolRouteRepository.save(route);
    }

    public PatrolRoute update(UUID id, UUID companyId, UpdateRouteCommand command) {
        PatrolRoute route = getById(id, companyId);
        if (command.name() != null) {
            route.setName(command.name());
        }
        if (command.type() != null) {
            route.setType(command.type());
        }
        if (command.active() != null) {
            route.setActive(command.active());
        }
        return patrolRouteRepository.save(route);
    }

    public void deactivate(UUID id, UUID companyId) {
        PatrolRoute route = getById(id, companyId);
        route.setActive(false);
        patrolRouteRepository.save(route);
    }

    public PatrolRoutePoint addPoint(UUID routeId, UUID companyId, AddRoutePointCommand command) {
        PatrolRoute route = getById(routeId, companyId);
        PatrolPoint point = patrolPointRepository.findByIdAndSiteIdIn(command.pointId(), siteLookup.siteIdsOf(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Patrol point not found"));

        if (!point.getSiteId().equals(route.getSiteId())) {
            throw new BusinessValidationException("Patrol point must belong to the same site as the route");
        }
        if (patrolRoutePointRepository.existsByRouteIdAndSequence(routeId, command.sequence())) {
            throw new ConflictException("A route point already exists at sequence " + command.sequence());
        }

        PatrolRoutePoint routePoint = new PatrolRoutePoint();
        routePoint.setRoute(route);
        routePoint.setPoint(point);
        routePoint.setSequence(command.sequence());
        routePoint.setRequired(command.required());
        return patrolRoutePointRepository.save(routePoint);
    }

    /**
     * Full reorder/replace: takes the complete ordered list of points for the
     * route and rewrites sequences accordingly. Simpler and safer than
     * incremental reordering (no risk of transient duplicate-sequence
     * states within the same transaction).
     */
    public List<PatrolRoutePoint> reorderPoints(UUID routeId, UUID companyId, List<AddRoutePointCommand> orderedPoints) {
        PatrolRoute route = getById(routeId, companyId);
        var siteIds = siteLookup.siteIdsOf(companyId);

        List<PatrolRoutePoint> existing = patrolRoutePointRepository.findByRouteIdOrderBySequenceAsc(routeId);
        patrolRoutePointRepository.deleteAll(existing);
        patrolRoutePointRepository.flush();

        return orderedPoints.stream()
                .map(cmd -> {
                    PatrolPoint point = patrolPointRepository.findByIdAndSiteIdIn(cmd.pointId(), siteIds)
                            .orElseThrow(() -> new ResourceNotFoundException("Patrol point not found: " + cmd.pointId()));
                    if (!point.getSiteId().equals(route.getSiteId())) {
                        throw new BusinessValidationException(
                                "Patrol point must belong to the same site as the route: " + cmd.pointId());
                    }
                    PatrolRoutePoint routePoint = new PatrolRoutePoint();
                    routePoint.setRoute(route);
                    routePoint.setPoint(point);
                    routePoint.setSequence(cmd.sequence());
                    routePoint.setRequired(cmd.required());
                    return patrolRoutePointRepository.save(routePoint);
                })
                .toList();
    }

    public void removePoint(UUID routeId, UUID pointId, UUID companyId) {
        getById(routeId, companyId); // tenant check
        patrolRoutePointRepository.deleteByRouteIdAndPointId(routeId, pointId);
    }
}
