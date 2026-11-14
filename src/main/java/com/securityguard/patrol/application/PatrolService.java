package com.securityguard.patrol.application;

import com.securityguard.patrol.domain.Patrol;
import com.securityguard.patrol.domain.repository.PatrolRepository;
import com.securityguard.patrol.application.port.in.PatrolUseCase;
import com.securityguard.patrol.application.port.out.SiteLookupPort;
import com.securityguard.shared.exception.ResourceNotFoundException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@Transactional
public class PatrolService implements PatrolUseCase {

    private final PatrolRepository patrolRepository;
    private final SiteLookupPort siteLookup;

    public PatrolService(PatrolRepository patrolRepository, SiteLookupPort siteLookup) {
        this.patrolRepository = patrolRepository;
        this.siteLookup = siteLookup;
    }

    public record CreatePatrolCommand(UUID siteId, String name, String description) {
    }

    public record UpdatePatrolCommand(String name, String description, Boolean active) {
    }

    @Transactional(readOnly = true)
    public Page<Patrol> list(UUID companyId, Pageable pageable) {
        return patrolRepository.findBySiteIdIn(siteLookup.siteIdsOf(companyId), pageable);
    }

    @Transactional(readOnly = true)
    public Patrol getById(UUID id, UUID companyId) {
        return patrolRepository.findByIdAndSiteIdIn(id, siteLookup.siteIdsOf(companyId))
                .orElseThrow(() -> new ResourceNotFoundException("Patrol not found"));
    }

    public Patrol create(UUID companyId, CreatePatrolCommand command) {
        siteLookup.requireSite(companyId, command.siteId());

        Patrol patrol = new Patrol();
        patrol.setSiteId(command.siteId());
        patrol.setName(command.name());
        patrol.setDescription(command.description());
        patrol.setActive(true);
        return patrolRepository.save(patrol);
    }

    public Patrol update(UUID id, UUID companyId, UpdatePatrolCommand command) {
        Patrol patrol = getById(id, companyId);
        if (command.name() != null) {
            patrol.setName(command.name());
        }
        if (command.description() != null) {
            patrol.setDescription(command.description());
        }
        if (command.active() != null) {
            patrol.setActive(command.active());
        }
        return patrolRepository.save(patrol);
    }

    public void deactivate(UUID id, UUID companyId) {
        Patrol patrol = getById(id, companyId);
        patrol.setActive(false);
        patrolRepository.save(patrol);
    }
}
