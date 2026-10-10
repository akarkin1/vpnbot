package org.github.akarkin1.config;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.regions.Region;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Caches the supported regions and each region's runtime parameters for {@code ttl}.
 * A zero {@code ttl} disables caching.
 */
@RequiredArgsConstructor
public class CachedTaskConfigService implements TaskConfigService {

  private final TaskConfigService delegate;
  private final Duration ttl;
  private final Clock clock;

  private final Map<Region, CachedValue<TaskRuntimeParameters>> runtimeParameters =
      new ConcurrentHashMap<>();
  private volatile CachedValue<List<Region>> supportedRegions;

  @Override
  public List<Region> getSupportedRegions() {
    CachedValue<List<Region>> cached = supportedRegions;
    if (isExpired(cached)) {
      cached = cache(delegate.getSupportedRegions());
      supportedRegions = cached;
    }
    return cached.value();
  }

  @Override
  public TaskRuntimeParameters getTaskRuntimeParameters(Region region) {
    CachedValue<TaskRuntimeParameters> cached = runtimeParameters.get(region);
    if (isExpired(cached)) {
      cached = cache(delegate.getTaskRuntimeParameters(region));
      runtimeParameters.put(region, cached);
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
