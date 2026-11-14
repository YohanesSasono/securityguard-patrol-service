package com.securityguard.patrol.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Sites (and their company) are owned by identity-service; patrol stores only {@code site_id}. Every tenant check in
 * patrol goes through this port: "belongs to the caller's company" == "site_id is one of the company's sites".
 */
public interface SiteLookupPort {

    /** Read-only view of a site owned by identity-service. */
    record SiteRef(UUID id, UUID companyId, String name) {
    }

    /**
     * All site ids of the company. Never empty (an unknown sentinel id is returned instead) so it can be passed
     * straight into a JPQL {@code IN :siteIds}. May be cached briefly.
     */
    Set<UUID> siteIdsOf(UUID companyId);

    /** Throws ResourceNotFoundException when the site does not belong to the company. */
    void requireSite(UUID companyId, UUID siteId);

    Optional<SiteRef> findSite(UUID companyId, UUID siteId);

    /** Names of the given sites; sites that are missing or not accessible are left out. */
    Map<UUID, String> siteNamesOf(UUID companyId, Collection<UUID> siteIds);
}
