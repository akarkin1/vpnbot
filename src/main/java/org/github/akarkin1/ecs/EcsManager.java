package org.github.akarkin1.ecs;

import software.amazon.awssdk.regions.Region;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface EcsManager {

  TaskInfo startTask(Region region,
                     String hostName,
                     Map<String, String> tags,
                     Map<String, String> environment);

  List<TaskInfo> listTasks(Map<String, String> matchingTags);

  Optional<TaskInfo> getTask(Region region, String taskId);

  void stopTask(Region region, String taskId, String reason);

  Set<String> getSupportedRegions();

}
