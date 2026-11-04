package com.securityguard.patrol.domain;

import com.securityguard.shared.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "patrol_route_points")
@Getter
@Setter
@NoArgsConstructor
public class PatrolRoutePoint extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "route_id", nullable = false)
    private PatrolRoute route;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "point_id", nullable = false)
    private PatrolPoint point;

    @Column(name = "sequence", nullable = false)
    private Integer sequence;

    @Column(name = "is_required", nullable = false)
    private boolean required = true;
}
