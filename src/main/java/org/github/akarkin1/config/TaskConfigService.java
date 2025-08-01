package org.github.akarkin1.config;

import software.amazon.awssdk.regions.Region;

import java.util.List;
import java.util.Set;

public interface TaskConfigService {

  Set<String> getSupportedServices();

  List<Region> getSupportedRegions(String serviceName);

  /**
   * Get task runtime parameters for a region and service type.
   * @param region the region
   * @param serviceName the service type (e.g., "vpn", "minecraft")
   * @return the task runtime parameters
   */
  TaskRuntimeParameters getTaskRuntimeParameters(Region region, String serviceName);

}
