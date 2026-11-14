package com.securityguard.patrol.application.port.in;

import com.securityguard.patrol.application.PatrolPointService;
import com.securityguard.patrol.domain.PatrolPoint;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface PatrolPointUseCase {
    Page<PatrolPoint> list(UUID companyId, UUID siteId, Pageable pageable);
    PatrolPoint getById(UUID id, UUID companyId);
    PatrolPoint create(UUID companyId, PatrolPointService.CreatePointCommand command);
    PatrolPoint update(UUID id, UUID companyId, PatrolPointService.UpdatePointCommand command);
    void deactivate(UUID id, UUID companyId);
}
