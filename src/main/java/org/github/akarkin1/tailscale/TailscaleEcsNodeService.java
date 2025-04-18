package org.github.akarkin1.tailscale;

import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.config.YamlApplicationConfiguration.AWSConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.EcsConfiguration;
import org.github.akarkin1.ecs.EcsManager;
import org.github.akarkin1.ecs.RunTaskStatus;
import org.github.akarkin1.ecs.TaskInfo;
import software.amazon.awssdk.regions.Region;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
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
    Region region = regionByCity.getOrDefault(userRegion, Region.of(userRegion));
    return ecsManager.getSupportedRegions().contains(region.id());
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
      hostName = chooseHostName(userTgId, region.id());
    }

    Map<String, String> assignedTags = Map.of(
        config.getHostNameTag(), hostName,
        config.getRunByTag(), userTgId,
        config.getServiceNameTag(), config.getServiceName()
    );
    return ecsManager.startTask(region, hostName, assignedTags);
  }

  @Override
  public Optional<TaskInfo> getFullTaskInfo(Region region, String clusterName, String taskId) {
    return ecsManager.getFullTaskInfo(region, clusterName, taskId);
  }

  @Override
  public RunTaskStatus checkNodeStatus(TaskInfo taskInfo) {
    return ecsManager.checkTaskHealth(taskInfo.getRegion(),
                                      taskInfo.getCluster(),
                                      taskInfo.getId());
  }

  private String chooseHostName(String userTgId, String regionId) {
    Set<String> hostNames = ecsManager.listTasks(Map.of(
            config.getServiceNameTag(), config.getServiceName()
        ))
        .stream()
        .map(TaskInfo::getHostName)
        .collect(Collectors.toSet());

    String suggestedHostName;
    int nodeNumber = 1;
    do {
      suggestedHostName = suggestHostName(userTgId, regionId, nodeNumber);
      nodeNumber++;
    } while (hostNames.contains(suggestedHostName));

    return suggestedHostName;
  }

  private String suggestHostName(String userTgId, String regionId, int nodeNumber) {
    String city = cityByRegion.get(regionId);
    return sanitizeHostName("%s-%s-%d".formatted(userTgId, city, nodeNumber));
  }

  static String sanitizeHostName(String hostName) {
    return hostName.toLowerCase().replaceAll("[_.\\s]", "");
  }

  @Override
  public List<TaskInfo> listTasks(String userTgId) {
    Map<String, String> matchingTags = new HashMap<>();
    matchingTags.put(config.getServiceNameTag(), config.getServiceName());
    if (userTgId != null) {
      matchingTags.put(config.getRunByTag(), userTgId);
    }

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
