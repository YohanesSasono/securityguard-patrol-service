package com.securityguard.patrol.application.port.out;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/** Users and memberships are owned by identity-service; patrol stores only user ids. */
public interface UserLookupPort {

    /** Throws ResourceNotFoundException when the user is not a member of the company. */
    void requireMember(UUID companyId, UUID userId);

    /**
     * Display names of the given users. Users that cannot be resolved (missing, or the caller's role may not read
     * other users' names) are left out, so callers must tolerate a missing entry.
     */
    Map<UUID, String> userNamesOf(UUID companyId, Collection<UUID> userIds);
}
