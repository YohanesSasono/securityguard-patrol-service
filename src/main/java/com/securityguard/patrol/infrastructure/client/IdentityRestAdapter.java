package com.securityguard.patrol.infrastructure.client;

import com.securityguard.patrol.application.port.out.SiteLookupPort;
import com.securityguard.patrol.application.port.out.UserLookupPort;
import com.securityguard.resilience.CircuitBreakerRegistry;
import com.securityguard.resilience.ExternalCallGuard;
import com.securityguard.observability.metrics.MetricsRecorder;
import com.securityguard.shared.exception.ResourceNotFoundException;
import com.securityguard.shared.http.SharedRestClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Site and user lookups against identity-service, with the caller's JWT (identity scopes everything to the
 * company in that token, so another tenant's site/user is a 404).
 *
 * <ul>
 *   <li>Site ids per company are cached for {@code services.identity.site-cache-ttl} (default 45s). On a miss in
 *       {@link #requireSite} the cache is refreshed once, so a site created a moment ago is found.</li>
 *   <li>Calls go through the {@code identity} circuit breaker ({@code app.resilience.breakers.identity}); only
 *       5xx/IO errors count as failures, 4xx does not.</li>
 * </ul>
 */
@Component
public class IdentityRestAdapter implements SiteLookupPort, UserLookupPort {

    /** Never a real site id; keeps {@code IN :siteIds} valid for a company without sites. */
    static final UUID NO_SITE = new UUID(0L, 0L);

    private static final ParameterizedTypeReference<List<UUID>> UUID_LIST = new ParameterizedTypeReference<>() { };

    private final SharedRestClient restClient;
    private final String baseUrl;
    private final Duration siteCacheTtl;
    private final ExternalCallGuard guard;
    private final Clock clock = Clock.systemUTC();
    private final Map<UUID, CachedIds> siteIdCache = new ConcurrentHashMap<>();

    public IdentityRestAdapter(SharedRestClient restClient,
                               @Value("${services.identity.base-url}") String baseUrl,
                               @Value("${services.identity.site-cache-ttl:45s}") Duration siteCacheTtl,
                               CircuitBreakerRegistry breakers,
                               MetricsRecorder metrics) {
        this.restClient = restClient;
        this.baseUrl = baseUrl;
        this.siteCacheTtl = siteCacheTtl;
        this.guard = new ExternalCallGuard("identity", breakers.get("identity"), null, metrics);
    }

    // ---------------------------------------------------------------- sites

    @Override
    public Set<UUID> siteIdsOf(UUID companyId) {
        CachedIds cached = siteIdCache.get(companyId);
        if (cached != null && clock.instant().isBefore(cached.expiresAt())) {
            return cached.ids();
        }
        return refreshSiteIds(companyId);
    }

    @Override
    public void requireSite(UUID companyId, UUID siteId) {
        if (siteId == null) {
            throw new ResourceNotFoundException("Site not found");
        }
        if (siteIdsOf(companyId).contains(siteId) || refreshSiteIds(companyId).contains(siteId)) {
            return;
        }
        throw new ResourceNotFoundException("Site not found");
    }

    @Override
    public Optional<SiteRef> findSite(UUID companyId, UUID siteId) {
        try {
            SiteResponse site = call(() -> restClient.exchange(HttpMethod.GET, uri("sites", siteId.toString()), null,
                    CurrentBearerToken.headers(), SiteResponse.class));
            return site == null ? Optional.empty() : Optional.of(new SiteRef(site.id(), companyId, site.name()));
        } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden e) {
            return Optional.empty();
        }
    }

    @Override
    public Map<UUID, String> siteNamesOf(UUID companyId, Collection<UUID> siteIds) {
        Map<UUID, String> names = new LinkedHashMap<>();
        for (UUID siteId : Set.copyOf(siteIds)) {
            findSite(companyId, siteId).ifPresent(site -> names.put(site.id(), site.name()));
        }
        return names;
    }

    private Set<UUID> refreshSiteIds(UUID companyId) {
        List<UUID> ids = call(() -> restClient.exchange(HttpMethod.GET, uri("sites", "ids"), null,
                CurrentBearerToken.headers(), UUID_LIST));
        Set<UUID> result = ids == null || ids.isEmpty() ? Set.of(NO_SITE) : Set.copyOf(ids);
        siteIdCache.put(companyId, new CachedIds(result, clock.instant().plus(siteCacheTtl)));
        return result;
    }

    // ---------------------------------------------------------------- users

    @Override
    public void requireMember(UUID companyId, UUID userId) {
        try {
            call(() -> restClient.exchange(HttpMethod.GET, uri("users", userId.toString(), "display-name"), null,
                    CurrentBearerToken.headers(), UserNameResponse.class));
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResourceNotFoundException("User not found");
        }
    }

    @Override
    public Map<UUID, String> userNamesOf(UUID companyId, Collection<UUID> userIds) {
        Map<UUID, String> names = new LinkedHashMap<>();
        for (UUID userId : Set.copyOf(userIds)) {
            try {
                UserNameResponse user = call(() -> restClient.exchange(HttpMethod.GET,
                        uri("users", userId.toString(), "display-name"), null,
                        CurrentBearerToken.headers(), UserNameResponse.class));
                if (user != null) {
                    names.put(user.id(), user.fullName());
                }
            } catch (HttpClientErrorException.NotFound | HttpClientErrorException.Forbidden e) {
                // missing user, or the caller's role may not read names (e.g. SECURITY_OFFICER)
            }
        }
        return names;
    }

    // ---------------------------------------------------------------- helpers

    private <T> T call(Supplier<T> action) {
        return guard.call(action, IdentityRestAdapter::isDependencyFailure, null);
    }

    private static boolean isDependencyFailure(Throwable e) {
        return !(e instanceof HttpClientErrorException);
    }

    private URI uri(String... segments) {
        return UriComponentsBuilder.fromUriString(baseUrl)
                .pathSegment("api", "v1")
                .pathSegment(segments)
                .build().toUri();
    }

    private record CachedIds(Set<UUID> ids, Instant expiresAt) {
    }

    private record SiteResponse(UUID id, String name) {
    }

    private record UserNameResponse(UUID id, String fullName) {
    }
}
