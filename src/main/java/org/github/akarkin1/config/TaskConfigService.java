package org.github.akarkin1.config;

import software.amazon.awssdk.regions.Region;

import java.util.List;

public interface TaskConfigService {

  List<Region> getSupportedRegions();

  TaskRuntimeParameters getTaskRuntimeParameters(Region region);

}
