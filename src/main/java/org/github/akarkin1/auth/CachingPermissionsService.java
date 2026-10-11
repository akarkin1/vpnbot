package org.github.akarkin1.auth;

import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Caches the users and their permissions for {@code ttl}; an update invalidates the cache.
 * A zero {@code ttl} disables caching.
 */
@RequiredArgsConstructor
public class CachingPermissionsService implements PermissionsService {

  private final PermissionsService delegate;
  private final Duration ttl;
  private final Clock clock;

  private volatile CachedUsers cachedUsers;

  @Override
  public Map<TgUser, List<Permission>> getUsers() {
    CachedUsers cached = cachedUsers;
    if (cached == null || !clock.instant().isBefore(cached.expiresAt())) {
      cached = new CachedUsers(delegate.getUsers(), clock.instant().plus(ttl));
      cachedUsers = cached;
    }

    return cached.value();
  }

  @Override
  public void updateUserPermissions(TgUser user, Set<Permission> permissions) {
    // invalidate cache
    cachedUsers = null;
    delegate.updateUserPermissions(user, permissions);
  }

  private record CachedUsers(Map<TgUser, List<Permission>> value, Instant expiresAt) {}

}
