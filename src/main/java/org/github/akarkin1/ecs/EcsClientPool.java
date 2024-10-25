package org.github.akarkin1.ecs;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;

import java.util.HashMap;
import java.util.Map;

public class EcsClientPool implements EcsClientProvider {

  private final Map<Region, EcsClient> regionalInstances = new HashMap<>();
  private final EcsClient defaultInstance;
  private final EcsClientProvider delegate;

  EcsClientPool(EcsClientProvider delegate) {
    this.delegate = delegate;
    this.defaultInstance = delegate.get();
  }

  public EcsClientPool() {
    this.delegate = new SimpleEcsClientProvider();
    this.defaultInstance = delegate.get();
  }

  @Override
  public EcsClient get() {
    return defaultInstance;
  }

  @Override
  public EcsClient get(Region region) {
    return regionalInstances.computeIfAbsent(region, this.delegate::get);
  }
}
