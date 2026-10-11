package org.github.akarkin1.tailscale;

import org.github.akarkin1.ecs.EcsNode;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface TailscaleNodeService {

  boolean isRegionValid(String userRegion);

  boolean isRegionSupported(String userRegion);

  boolean isHostnameAvailable(String userRegion, String userHostName);

  EcsNode runNode(String regionId, NodeOrigin origin, String hostName,
                  Map<String, String> environment);

  Optional<EcsNode> getNode(String regionId, String taskId);

  void stopNode(String regionId, String taskId, String reason);

  String toRegionId(String userRegion);

  /** The nodes started by the user with this Telegram user id (its {@code RunBy} tag); null: all nodes. */
  List<EcsNode> listNodes(String userId);

  List<String> getSupportedRegionDescriptions();

  Set<String> getSupportedRegionIds();

}