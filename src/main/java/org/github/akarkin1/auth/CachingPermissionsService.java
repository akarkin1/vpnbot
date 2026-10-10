package org.github.akarkin1.auth;

import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Caches the user permissions for {@code ttl}; an update invalidates the cache.
 * A zero {@code ttl} disables caching.
 */
@RequiredArgsConstructor
public class CachingPermissionsService implements PermissionsService {

  private final PermissionsService delegate;
  private final Duration ttl;
  private final Clock clock;

  private volatile CachedPermissions cachedPermissions;

  @Override
  public Map<String, List<Permission>> getUserPermissions() {
    CachedPermissions cached = cachedPermissions;
    if (cached == null || !clock.instant().isBefore(cached.expiresAt())) {
      cached = new CachedPermissions(delegate.getUserPermissions(), clock.instant().plus(ttl));
      cachedPermissions = cached;
    }

    return cached.value();
  }

  @Override
  public void updateUserPermissions(String tgUsername, Set<Permission> actions) {
    // invalidate cache
    cachedPermissions = null;
    delegate.updateUserPermissions(tgUsername, actions);
  }

  private record CachedPermissions(Map<String, List<Permission>> value, Instant expiresAt) {}

}
