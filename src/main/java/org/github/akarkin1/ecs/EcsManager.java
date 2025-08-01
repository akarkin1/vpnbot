package org.github.akarkin1.ecs;

import software.amazon.awssdk.regions.Region;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface EcsManager {

  TaskInfo startTask(Region region,
                     String hostName,
                     String serviceName, Map<String, String> tags);

  RunTaskStatus checkTaskHealth(Region region, String clusterName, String taskId);

  List<TaskInfo> listTasks(Map<String, String> matchingTags);

  Set<String> getSupportedRegions(String serviceName);

  Optional<TaskInfo> getFullTaskInfo(Region region, String clusterName, String taskId);
}
