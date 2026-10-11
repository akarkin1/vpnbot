package org.github.akarkin1.ecs;

import software.amazon.awssdk.regions.Region;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface EcsManager {

  EcsNode startTask(Region region,
                    String hostName,
                    Map<String, String> tags,
                    Map<String, String> environment);

  List<EcsNode> listTasks(Map<String, String> matchingTags);

  Optional<EcsNode> getTask(Region region, String taskId);

  void stopTask(Region region, String taskId, String reason);

  Set<String> getSupportedRegions();

}
