package org.github.akarkin1.tailscale;

import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.config.YamlApplicationConfiguration.AWSConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.EcsConfiguration;
import org.github.akarkin1.ecs.EcsManager;
import org.github.akarkin1.ecs.RunTaskStatus;
import org.github.akarkin1.ecs.TaskInfo;
import software.amazon.awssdk.regions.Region;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

public class TailscaleEcsNodeService implements TailscaleNodeService {

  private final EcsManager ecsManager;
  private final EcsConfiguration config;
  private final Map<String, Region> regionByCity;
  private final Set<String> knownRegions;
  private final Map<String, String> cityByRegion;

  public TailscaleEcsNodeService(EcsManager ecsManager,
                                 EcsConfiguration ecsConfig,
                                 AWSConfiguration awsConfig) {
    this.ecsManager = ecsManager;
    this.config = ecsConfig;
    this.regionByCity = awsConfig.getRegionCities()
        .entrySet()
        .stream()
        .collect(Collectors.toMap(Entry::getValue,
                                  entry -> Region.of(entry.getKey())));
    this.cityByRegion = awsConfig.getRegionCities();
    this.knownRegions = awsConfig.getRegionCities().keySet();
  }

  @Override
  public boolean isRegionValid(String userRegion) {
    if (knownRegions.contains(userRegion)) {
      return true;
    }

    return regionByCity.containsKey(userRegion);
  }

  @Override
  public boolean isRegionSupported(String userRegion) {
    return ecsManager.getSupportedRegions().contains(userRegion);
  }

  @Override
  public boolean isHostnameAvailable(String userRegion, String userHostName) {
    if (StringUtils.isBlank(userHostName)) {
      return false;
    }

    Map<String, String> matchingTags = Map.of(
        config.getServiceNameTag(), config.getServiceName(),
        config.getHostNameTag(), userHostName
    );
    return ecsManager.listTasks(matchingTags).isEmpty();
  }

  @Override
  public TaskInfo runNode(String userRegion, String userTgId, String userHostName) {
    Region region = regionByCity.getOrDefault(userRegion, Region.of(userRegion));

    String hostName = userHostName;
    if (userHostName == null) {
      hostName = chooseHostName(userTgId);
    }

    Map<String, String> assignedTags = Map.of(
        config.getHostNameTag(), hostName,
        config.getRunByTag(), userTgId,
        config.getServiceNameTag(), config.getServiceName()
    );
    return ecsManager.startTask(region, hostName, assignedTags);
  }

  @Override
  public RunTaskStatus checkNodeStatus(TaskInfo taskInfo) {
    return ecsManager.checkTaskHealth(taskInfo.getRegion(),
                                      taskInfo.getCluster(),
                                      taskInfo.getId());
  }

  private String chooseHostName(String userTgId) {
    Set<String> hostNames = ecsManager.listTasks(Collections.emptyMap())
        .stream()
        .map(TaskInfo::getHostName)
        .collect(Collectors.toSet());

    String suggestedHostName;
    int nodeNumber = 1;
    do {
      suggestedHostName = suggestHostName(userTgId, nodeNumber);
      nodeNumber++;
    } while (hostNames.contains(suggestedHostName));

    return suggestedHostName;
  }

  private static String suggestHostName(String userTgId, int nodeNumber) {
    return "%s-node-%d".formatted(userTgId, nodeNumber);
  }

  @Override
  public List<TaskInfo> listTasks(String userTgId) {
    Map<String, String> matchingTags = Map.of(
        config.getServiceNameTag(), config.getServiceName(),
        config.getRunByTag(), userTgId);
    return ecsManager.listTasks(matchingTags);
  }

  @Override
  public List<String> getSupportedRegionDescriptions() {
    return ecsManager.getSupportedRegions()
        .stream()
        .map(region -> "%s (%s)".formatted(this.cityByRegion.get(region), region))
        .toList();
  }
}
