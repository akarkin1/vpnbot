package org.github.akarkin1.config;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.regions.Region;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

@RequiredArgsConstructor
public class CachedS3TaskConfigService implements TaskConfigService {

  private final TaskConfigService delegate;
  private final Duration ttl;
  private final Clock clock;

  @Override
  public List<Region> getSupportedRegions() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public TaskRuntimeParameters getTaskRuntimeParameters(Region region) {
    throw new UnsupportedOperationException("Not implemented yet");
  }
}
