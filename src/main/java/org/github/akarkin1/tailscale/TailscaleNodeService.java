package org.github.akarkin1.tailscale;

import org.github.akarkin1.ecs.TaskInfo;

import java.util.List;
import java.util.Map;
import java.util.Set;

public interface TailscaleNodeService {

  boolean isRegionValid(String userRegion);

  boolean isRegionSupported(String userRegion);

  boolean isHostnameAvailable(String userRegion, String userHostName);

  TaskInfo runNode(String userRegion, String userTgId, String userHostName,
                   Map<String, String> environment);

  String toRegionId(String userRegion);

  List<TaskInfo> listTasks(String userTgId);

  List<String> getSupportedRegionDescriptions();

  Set<String> getSupportedRegionIds();

}