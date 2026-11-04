package com.securityguard.patrol.domain;

import com.securityguard.shared.entity.AuditableEntity;
import com.securityguard.site.domain.Site;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

/**
 * type = CHECKPOINT | EQUIPMENT. CHECKPOINT requires nfcUid (validated in
 * PatrolPointService). inspectionStatus/inspectionNotes/photoPath double as
 * a "last known equipment state" snapshot, updated whenever an execution
 * completes an inspection at this point - useful for dashboards that want
 * current equipment status without joining through execution history. The
 * authoritative per-visit record is always PatrolExecutionPoint.
 */
@Entity
@Table(name = "patrol_points")
@Getter
@Setter
@NoArgsConstructor
public class PatrolPoint extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private PatrolPointType type;

    @Column(name = "nfc_uid", length = 100)
    private String nfcUid;

    @Column(name = "location", columnDefinition = "geography(Point,4326)")
    private Point location;

    @Column(name = "inspection_status", length = 30)
    private String inspectionStatus;

    @Column(name = "inspection_notes", columnDefinition = "text")
    private String inspectionNotes;

    @Column(name = "photo_path", length = 500)
    private String photoPath;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
