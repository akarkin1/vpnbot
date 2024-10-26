package org.github.akarkin1.tailscale;

import org.github.akarkin1.ecs.RunTaskStatus;
import org.github.akarkin1.ecs.TaskInfo;
import software.amazon.awssdk.regions.Region;


import java.util.List;
import java.util.Optional;

public interface TailscaleNodeService {

  boolean isRegionValid(String userRegion);

  boolean isRegionSupported(String userRegion);

  boolean isHostnameAvailable(String userRegion, String userHostName);

  TaskInfo runNode(String userRegion, String userTgId, String userHostName);

  Optional<TaskInfo> getFullTaskInfo(Region region, String userTgId, String userHostName);

  RunTaskStatus checkNodeStatus(TaskInfo taskInfo);

  List<TaskInfo> listTasks(String userTgId);

  List<String> getSupportedRegionDescriptions();

}