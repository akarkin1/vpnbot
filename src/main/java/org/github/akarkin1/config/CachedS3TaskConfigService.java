package org.github.akarkin1.config;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import software.amazon.awssdk.regions.Region;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class CachedS3TaskConfigService implements TaskConfigService {

  private final TaskConfigService delegate;
  private final S3Configuration config;
  private final Map<Region, TaskRuntimeParameters> runtimeParameters = new HashMap<>();

  private List<Region> supportedRegions;


  @Override
  public List<Region> getSupportedRegions() {
    if (!Boolean.TRUE.equals(config.getCacheSupportedRegions())) {
        return delegate.getSupportedRegions();
    }

    if (supportedRegions == null) {
      supportedRegions = delegate.getSupportedRegions();
    }

    return supportedRegions;
  }

  @Override
  public TaskRuntimeParameters getTaskRuntimeParameters(Region region) {
    if (!Boolean.TRUE.equals(config.getCacheTaskRuntimeParameters())) {
      return delegate.getTaskRuntimeParameters(region);
    }

    return runtimeParameters.computeIfAbsent(region,
                                             ignore -> delegate.getTaskRuntimeParameters(region));
  }
}
