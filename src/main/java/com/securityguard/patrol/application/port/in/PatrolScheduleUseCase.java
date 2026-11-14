package com.securityguard.patrol.application.port.in;

import com.securityguard.patrol.application.PatrolScheduleService;
import com.securityguard.patrol.domain.PatrolSchedule;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.util.UUID;

public interface PatrolScheduleUseCase {
    Page<PatrolSchedule> list(UUID companyId, Pageable pageable);
    Page<PatrolSchedule> listForUser(UUID userId, UUID companyId, Pageable pageable);
    PatrolSchedule getById(UUID id, UUID companyId);
    PatrolSchedule create(UUID companyId, PatrolScheduleService.CreateScheduleCommand command);
    PatrolSchedule update(UUID id, UUID companyId, PatrolScheduleService.UpdateScheduleCommand command);
    void cancel(UUID id, UUID companyId);
}
