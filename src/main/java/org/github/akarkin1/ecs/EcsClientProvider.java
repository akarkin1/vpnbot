package org.github.akarkin1.ecs;

import software.amazon.awssdk.services.ecs.EcsClient;

public interface EcsClientProvider {

  EcsClient getEcsClient();

  EcsClient getEcsClient(String region);
}
