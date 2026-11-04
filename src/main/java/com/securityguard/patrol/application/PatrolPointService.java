package com.securityguard.patrol.application;

import com.securityguard.patrol.domain.PatrolPoint;
import com.securityguard.patrol.domain.repository.PatrolPointRepository;
import com.securityguard.patrol.domain.PatrolPointType;
import com.securityguard.shared.exception.BusinessValidationException;
import com.securityguard.shared.exception.ConflictException;
import com.securityguard.shared.exception.ResourceNotFoundException;
import com.securityguard.shared.geo.GeoPointFactory;
import com.securityguard.site.domain.Site;
import com.securityguard.site.domain.SiteRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * CHECKPOINT requires a valid (non-blank) NFC UID; EQUIPMENT must not have
 * one, since NFC verification only applies to checkpoints (DB_SCHEMA_V2.txt).
 * Uniqueness of nfc_uid is enforced by the partial unique index in V3 - this
 * service also pre-checks it to return a clean 409 instead of surfacing a
 * raw constraint-violation error, but still catches the DB exception as a
 * safety net against a race between two concurrent creates with the same UID.
 */
@Service
@Transactional
public class PatrolPointService {

    private final PatrolPointRepository patrolPointRepository;
    private final SiteRepository siteRepository;

    public PatrolPointService(PatrolPointRepository patrolPointRepository, SiteRepository siteRepository) {
        this.patrolPointRepository = patrolPointRepository;
        this.siteRepository = siteRepository;
    }

    public record CreatePointCommand(UUID siteId, String name, PatrolPointType type, String nfcUid,
                                      Double latitude, Double longitude) {
    }

    public record UpdatePointCommand(String name, String nfcUid, Double latitude, Double longitude,
                                      String inspectionStatus, String inspectionNotes, Boolean active) {
    }

    @Transactional(readOnly = true)
    public Page<PatrolPoint> list(UUID companyId, UUID siteId, Pageable pageable) {
        return siteId != null
                ? patrolPointRepository.findBySiteIdAndSiteCompanyId(siteId, companyId, pageable)
                : patrolPointRepository.findBySiteCompanyId(companyId, pageable);
    }

    @Transactional(readOnly = true)
    public PatrolPoint getById(UUID id, UUID companyId) {
        return patrolPointRepository.findByIdAndSiteCompanyId(id, companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Patrol point not found"));
    }

    public PatrolPoint create(UUID companyId, CreatePointCommand command) {
        validateNfcUidRule(command.type(), command.nfcUid());

        Site site = siteRepository.findByIdAndCompanyId(command.siteId(), companyId)
                .orElseThrow(() -> new ResourceNotFoundException("Site not found"));

        if (command.type() == PatrolPointType.CHECKPOINT && patrolPointRepository.existsByNfcUid(command.nfcUid())) {
            throw new ConflictException("NFC UID is already assigned to another patrol point");
        }

        PatrolPoint point = new PatrolPoint();
        point.setSite(site);
        point.setName(command.name());
        point.setType(command.type());
        point.setNfcUid(command.type() == PatrolPointType.CHECKPOINT ? command.nfcUid() : null);
        if (command.latitude() != null && command.longitude() != null) {
            point.setLocation(GeoPointFactory.point(command.latitude(), command.longitude()));
        }
        point.setActive(true);

        try {
            return patrolPointRepository.save(point);
        } catch (DataIntegrityViolationException e) {
            // Race: two concurrent creates with the same NFC UID both passed the
            // pre-check above; the DB's partial unique index is the real guarantee.
            throw new ConflictException("NFC UID is already assigned to another patrol point");
        }
    }

    public PatrolPoint update(UUID id, UUID companyId, UpdatePointCommand command) {
        PatrolPoint point = getById(id, companyId);

        if (command.nfcUid() != null) {
            validateNfcUidRule(point.getType(), command.nfcUid());
            point.setNfcUid(point.getType() == PatrolPointType.CHECKPOINT ? command.nfcUid() : null);
        }
        if (command.name() != null) {
            point.setName(command.name());
        }
        if (command.latitude() != null && command.longitude() != null) {
            point.setLocation(GeoPointFactory.point(command.latitude(), command.longitude()));
        }
        if (command.inspectionStatus() != null) {
            point.setInspectionStatus(command.inspectionStatus());
        }
        if (command.inspectionNotes() != null) {
            point.setInspectionNotes(command.inspectionNotes());
        }
        if (command.active() != null) {
            point.setActive(command.active());
        }

        try {
            return patrolPointRepository.save(point);
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("NFC UID is already assigned to another patrol point");
        }
    }

    public void deactivate(UUID id, UUID companyId) {
        PatrolPoint point = getById(id, companyId);
        point.setActive(false);
        patrolPointRepository.save(point);
    }

    private void validateNfcUidRule(PatrolPointType type, String nfcUid) {
        if (type == PatrolPointType.CHECKPOINT && (nfcUid == null || nfcUid.isBlank())) {
            throw new BusinessValidationException("CHECKPOINT points require a non-blank NFC UID");
        }
        if (type == PatrolPointType.EQUIPMENT && nfcUid != null && !nfcUid.isBlank()) {
            throw new BusinessValidationException("EQUIPMENT points must not have an NFC UID");
        }
    }
}
