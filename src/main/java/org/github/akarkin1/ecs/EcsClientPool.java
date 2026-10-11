package org.github.akarkin1.ecs;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/** One ECS client per region, created by {@code clients} on the region's first use. */
@RequiredArgsConstructor
public class EcsClientPool {

  private final Function<Region, EcsClient> clients;
  private final Map<Region, EcsClient> regionalInstances = new ConcurrentHashMap<>();

  public EcsClient get(Region region) {
    return regionalInstances.computeIfAbsent(region, clients);
  }
}
