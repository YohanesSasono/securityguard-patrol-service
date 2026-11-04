package com.securityguard.patrol.application;

import com.securityguard.incident.application.PatrolExecutionLookupPort;
import com.securityguard.patrol.domain.repository.PatrolExecutionRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class PatrolExecutionLookupAdapter implements PatrolExecutionLookupPort {

    private final PatrolExecutionRepository patrolExecutionRepository;

    public PatrolExecutionLookupAdapter(PatrolExecutionRepository patrolExecutionRepository) {
        this.patrolExecutionRepository = patrolExecutionRepository;
    }

    @Override
    public Optional<ExecutionRef> findForTenant(UUID executionId, UUID companyId) {
        return patrolExecutionRepository.findByIdAndSchedulePatrolSiteCompanyId(executionId, companyId)
                .map(execution -> new ExecutionRef(
                        execution.getId(),
                        execution.getSchedule().getPatrol().getSite().getId(),
                        companyId));
    }
}
