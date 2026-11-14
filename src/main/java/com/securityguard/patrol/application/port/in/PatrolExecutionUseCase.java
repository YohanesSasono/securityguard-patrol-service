package com.securityguard.patrol.application.port.in;

import com.securityguard.patrol.application.PatrolExecutionService;
import com.securityguard.patrol.domain.PatrolExecution;
import com.securityguard.patrol.domain.PatrolExecutionPoint;
import com.securityguard.security.AuthenticatedUser;
import java.util.List;
import java.util.UUID;

public interface PatrolExecutionUseCase {
    PatrolExecution getById(UUID executionId, UUID companyId, AuthenticatedUser actingUser);
    List<PatrolExecutionPoint> getExecutionPoints(UUID executionId, UUID companyId, AuthenticatedUser actingUser);
    PatrolExecution start(UUID scheduleId, UUID companyId, AuthenticatedUser actingUser);
    PatrolExecutionPoint verify(UUID executionId, UUID pointId, UUID companyId, AuthenticatedUser actingUser,
                                PatrolExecutionService.VerifyPointCommand command);
    PatrolExecutionPoint complete(UUID executionId, UUID pointId, UUID companyId, AuthenticatedUser actingUser,
                                  PatrolExecutionService.CompletePointCommand command);
}
