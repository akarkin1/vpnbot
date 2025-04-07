package org.github.akarkin1.ecs;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;

public class SimpleEcsClientProvider implements EcsClientProvider {

  @Override
  public EcsClient get() {
    return EcsClient.builder().build();
  }

  @Override
  public EcsClient get(Region region) {
    return EcsClient.builder()
        .region(region)
        .build();
  }
}
