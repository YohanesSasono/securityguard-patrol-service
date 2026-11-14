package com.securityguard.patrol.domain;

import com.securityguard.persistence.AuditableEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "patrols")
@Getter
@Setter
@NoArgsConstructor
public class Patrol extends AuditableEntity {

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "is_active", nullable = false)
    private boolean active = true;
}
