package org.github.akarkin1.config;

import lombok.RequiredArgsConstructor;
import lombok.val;
import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import software.amazon.awssdk.regions.Region;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public class CachedS3TaskConfigService implements TaskConfigService {

  private final TaskConfigService delegate;
  private final S3Configuration config;
  private final Map<ParametersCacheKey, TaskRuntimeParameters> runtimeParameters = new HashMap<>();

  private final Map<String, List<Region>> supportedRegions = new LinkedHashMap<>();

  @Override
  public Set<String> getSupportedServices() {
    return delegate.getSupportedServices();
  }

  @Override
  public List<Region> getSupportedRegions(String serviceName) {
    if (!Boolean.TRUE.equals(config.getCacheSupportedRegions())) {
        return delegate.getSupportedRegions(serviceName);
    }


    if (!supportedRegions.containsKey(serviceName)) {
      supportedRegions.put(serviceName, delegate.getSupportedRegions(serviceName));
    }

    return supportedRegions.get(serviceName);
  }

  @Override
  public TaskRuntimeParameters getTaskRuntimeParameters(Region region, String serviceName) {
    if (!Boolean.TRUE.equals(config.getCacheTaskRuntimeParameters())) {
      return delegate.getTaskRuntimeParameters(region, serviceName);
    }

    val key = new ParametersCacheKey(region, serviceName);

    if (runtimeParameters.containsKey(key)) {
      runtimeParameters.put(key, delegate.getTaskRuntimeParameters(region, serviceName));
    }

    return runtimeParameters.get(key);
  }

  private record ParametersCacheKey(Region region, String serviceName) {}
}
