package org.github.akarkin1.config;

import software.amazon.awssdk.regions.Region;

import java.util.List;

public interface TaskConfigService {

  List<Region> getSupportedRegions();

  /**
   * Get task runtime parameters for a region.
   * @param region the region
   * @return the task runtime parameters
   * @deprecated Use {@link #getTaskRuntimeParameters(Region, String)} instead
   */
  @Deprecated
  default TaskRuntimeParameters getTaskRuntimeParameters(Region region) {
    // Default to VPN service type for backward compatibility
    return getTaskRuntimeParameters(region, "vpn");
  }

  /**
   * Get task runtime parameters for a region and service type.
   * @param region the region
   * @param serviceType the service type (e.g., "vpn", "minecraft")
   * @return the task runtime parameters
   */
  TaskRuntimeParameters getTaskRuntimeParameters(Region region, String serviceType);

}
