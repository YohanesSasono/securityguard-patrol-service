package com.securityguard.patrol.domain;

import com.securityguard.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** site is kept directly on the route so "points must belong to the same site" is a simple equality check. */
@Entity
@Table(name = "patrol_routes")
@Getter
@Setter
@NoArgsConstructor
public class PatrolRoute extends AuditableEntity {

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 20)
    private RouteType type;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
