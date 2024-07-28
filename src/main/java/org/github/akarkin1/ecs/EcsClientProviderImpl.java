package org.github.akarkin1.ecs;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;

public class EcsClientProviderImpl implements EcsClientProvider {

  @Override
  public EcsClient getEcsClient() {
    return EcsClient.builder()
        .region(Region.AWS_GLOBAL)
        .build();
  }

  @Override
  public EcsClient getEcsClient(String region) {
    return EcsClient.builder()
        .region(Region.of(region))
        .build();
  }
}
