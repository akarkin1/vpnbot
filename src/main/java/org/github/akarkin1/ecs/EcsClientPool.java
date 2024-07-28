package org.github.akarkin1.ecs;

import software.amazon.awssdk.services.ecs.EcsClient;

import java.util.HashMap;
import java.util.Map;

public class EcsClientPool implements EcsClientProvider {

  private static final String GLOBAL_REGION = "GLOBAL";
  private final EcsClientProvider delegateProvider;
  private final Map<String, EcsClient> pool = new HashMap<>();

  public EcsClientPool() {
    this.delegateProvider = new EcsClientProviderImpl();
  }

  @Override
  public EcsClient getEcsClient() {
    return pool.computeIfAbsent(GLOBAL_REGION, delegateProvider::getEcsClient);
  }

  @Override
  public EcsClient getEcsClient(String region) {
    return pool.computeIfAbsent(region, delegateProvider::getEcsClient);
  }
}
