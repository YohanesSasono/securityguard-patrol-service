package com.securityguard.patrol.domain;

import com.securityguard.shared.entity.AuditableEntity;
import com.securityguard.site.domain.Site;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** site is kept directly on the route so "points must belong to the same site" is a simple equality check. */
@Entity
@Table(name = "patrol_routes")
@Getter
@Setter
@NoArgsConstructor
public class PatrolRoute extends AuditableEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private RouteType type;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
