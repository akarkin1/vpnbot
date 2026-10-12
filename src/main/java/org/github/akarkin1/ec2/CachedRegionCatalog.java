package org.github.akarkin1.ec2;

import lombok.RequiredArgsConstructor;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Caches the enabled regions for {@code ttl} (the config cache TTL), like
 * {@link org.github.akarkin1.config.CachedTaskConfigService}. A zero {@code ttl} disables caching.
 */
@RequiredArgsConstructor
public class CachedRegionCatalog implements RegionCatalog {

  private final RegionCatalog delegate;
  private final Duration ttl;
  private final Clock clock;

  private volatile CachedValue<Set<String>> enabledRegions;

  @Override
  public Set<String> enabledRegions() {
    CachedValue<Set<String>> cached = enabledRegions;
    if (isExpired(cached)) {
      cached = cache(delegate.enabledRegions());
      enabledRegions = cached;
    }
    return cached.value();
  }

  private <T> CachedValue<T> cache(T value) {
    return new CachedValue<>(value, clock.instant().plus(ttl));
  }

  private boolean isExpired(CachedValue<?> cached) {
    return cached == null || !clock.instant().isBefore(cached.expiresAt());
  }

  private record CachedValue<T>(T value, Instant expiresAt) {}

}
