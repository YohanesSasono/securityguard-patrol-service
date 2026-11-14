package com.securityguard.patrol.application;

import com.securityguard.patrol.domain.*;
import com.securityguard.patrol.domain.repository.PatrolRepository;
import com.securityguard.patrol.application.port.in.PatrolScheduleUseCase;
import com.securityguard.patrol.application.port.out.SiteLookupPort;
import com.securityguard.patrol.application.port.out.UserLookupPort;
import com.securityguard.patrol.domain.repository.PatrolRouteRepository;
import com.securityguard.patrol.domain.repository.PatrolScheduleRepository;
import com.securityguard.shared.exception.BusinessValidationException;
import com.securityguard.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * A schedule selects patrol + route + assigned user + date/time
 * (API_SPEC_V2.txt). Validates all three belong to the same tenant, and
 * patrol/route belong to the same site - not expressible as a DB constraint
 * since it spans three tables.
 */
@Service
@Transactional
public class PatrolScheduleService implements PatrolScheduleUseCase {

    private final PatrolScheduleRepository patrolScheduleRepository;
    private final PatrolRepository patrolRepository;
    private final PatrolRouteRepository patrolRouteRepository;
    private final SiteLookupPort siteLookup;
    private final UserLookupPort userLookup;

    public PatrolScheduleService(PatrolScheduleRepository patrolScheduleRepository,
                                  PatrolRepository patrolRepository,
                                  PatrolRouteRepository patrolRouteRepository,
                                  SiteLookupPort siteLookup,
                                  UserLookupPort userLookup) {
        this.patrolScheduleRepository = patrolScheduleRepository;
        this.patrolRepository = patrolRepository;
        this.patrolRouteRepository = patrolRouteRepository;
        this.siteLookup = siteLookup;
        this.userLookup = userLookup;
    }

    public record CreateScheduleCommand(UUID patrolId, UUID routeId, UUID assignedUserId,
                                         LocalDate scheduledDate, LocalTime startTime, LocalTime endTime) {
    }

    public record UpdateScheduleCommand(UUID assignedUserId, LocalDate scheduledDate, LocalTime startTime,
                                         LocalTime endTime, ScheduleStatus status) {
    }

    @Transactional(readOnly = true)
    public Page<PatrolSchedule> list(UUID companyId, Pageable pageable) {
        return patrolScheduleRepository.findByPatrolSiteIdIn(siteLookup.siteIdsOf(companyId), pageable);
    }

    @Transactional(readOnly = true)
    public Page<PatrolSchedule> listForUser(UUID userId, UUID companyId, Pageable pageable) {
        return patrolScheduleRepository.findByAssignedUserIdAndPatrolSiteIdIn(userId, siteLookup.siteIdsOf(companyId), pageable);
    }

    @Transactional(readOnly = true)
    public PatrolSchedule getById(UUID id, UUID companyId) {
        return patrolScheduleRepository.findByIdAndPatrolSiteIdIn(id, siteLookup.siteIdsOf(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Patrol schedule not found"));
    }

    public PatrolSchedule create(UUID companyId, CreateScheduleCommand command) {
        var siteIds = siteLookup.siteIdsOf(companyId);
        Patrol patrol = patrolRepository.findByIdAndSiteIdIn(command.patrolId(), siteIds)
                .orElseThrow(() -> new ResourceNotFoundException("Patrol not found"));
        PatrolRoute route = patrolRouteRepository.findByIdAndSiteIdIn(command.routeId(), siteIds)
                .orElseThrow(() -> new ResourceNotFoundException("Patrol route not found"));
        userLookup.requireMember(companyId, command.assignedUserId());

        if (!patrol.getSiteId().equals(route.getSiteId())) {
            throw new BusinessValidationException("Patrol and route must belong to the same site");
        }

        PatrolSchedule schedule = new PatrolSchedule();
        schedule.setPatrol(patrol);
        schedule.setRoute(route);
        schedule.setAssignedUserId(command.assignedUserId());
        schedule.setScheduledDate(command.scheduledDate());
        schedule.setStartTime(command.startTime());
        schedule.setEndTime(command.endTime());
        schedule.setStatus(ScheduleStatus.SCHEDULED);
        return patrolScheduleRepository.save(schedule);
    }

    public PatrolSchedule update(UUID id, UUID companyId, UpdateScheduleCommand command) {
        PatrolSchedule schedule = getById(id, companyId);

        if (command.assignedUserId() != null) {
            userLookup.requireMember(companyId, command.assignedUserId());
            schedule.setAssignedUserId(command.assignedUserId());
        }
        if (command.scheduledDate() != null) {
            schedule.setScheduledDate(command.scheduledDate());
        }
        if (command.startTime() != null) {
            schedule.setStartTime(command.startTime());
        }
        if (command.endTime() != null) {
            schedule.setEndTime(command.endTime());
        }
        if (command.status() != null) {
            schedule.setStatus(command.status());
        }
        return patrolScheduleRepository.save(schedule);
    }

    public void cancel(UUID id, UUID companyId) {
        PatrolSchedule schedule = getById(id, companyId);
        schedule.setStatus(ScheduleStatus.CANCELLED);
        patrolScheduleRepository.save(schedule);
    }
}
