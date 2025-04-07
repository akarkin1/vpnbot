package org.github.akarkin1.ecs;

import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.ecs.EcsClient;

public interface EcsClientProvider {

  EcsClient get();

  EcsClient get(Region region);

}
