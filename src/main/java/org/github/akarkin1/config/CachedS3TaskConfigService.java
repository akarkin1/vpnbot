package org.github.akarkin1.config;

import lombok.RequiredArgsConstructor;
import software.amazon.awssdk.regions.Region;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class CachedS3TaskConfigService implements TaskConfigService {

  private final TaskConfigService delegate;
  private final Map<Region, TaskRuntimeParameters> runtimeParameters = new HashMap<>();

  private List<Region> supportedRegions;


  @Override
  public List<Region> getSupportedRegions() {
    if (supportedRegions == null) {
      supportedRegions = delegate.getSupportedRegions();
    }

    return supportedRegions;
  }

  @Override
  public TaskRuntimeParameters getTaskRuntimeParameters(Region region) {
    return runtimeParameters.computeIfAbsent(region,
                                             ignore -> delegate.getTaskRuntimeParameters(region));
  }
}
