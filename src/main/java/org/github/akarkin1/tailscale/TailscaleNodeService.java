package org.github.akarkin1.tailscale;

import org.github.akarkin1.ecs.RunTaskStatus;
import org.github.akarkin1.ecs.TaskInfo;

import java.util.List;

public interface TailscaleNodeService {

  boolean isRegionValid(String userRegion);

  boolean isRegionSupported(String userRegion);

  boolean isHostnameAvailable(String userRegion, String userHostName);

  TaskInfo runNode(String userRegion, String userTgId, String userHostName);

  RunTaskStatus checkNodeStatus(TaskInfo taskInfo);

  List<TaskInfo> listTasks(String userTgId);

  List<String> getSupportedRegionDescriptions();

}