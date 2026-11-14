package com.securityguard.patrol.application.port.in;

import com.securityguard.patrol.application.PatrolService;
import com.securityguard.patrol.domain.Patrol;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface PatrolUseCase {
    Page<Patrol> list(UUID companyId, Pageable pageable);
    Patrol getById(UUID id, UUID companyId);
    Patrol create(UUID companyId, PatrolService.CreatePatrolCommand command);
    Patrol update(UUID id, UUID companyId, PatrolService.UpdatePatrolCommand command);
    void deactivate(UUID id, UUID companyId);
}
