package org.github.akarkin1.service;

import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.config.YamlApplicationConfiguration.AWSConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.EcsConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import org.github.akarkin1.ecs.EcsManager;
import org.github.akarkin1.ecs.RunTaskStatus;
import org.github.akarkin1.ecs.TaskInfo;
import software.amazon.awssdk.regions.Region;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Implementation of NodeService that uses ECS to run and manage nodes.
 */
public class EcsNodeService implements NodeService {

  private final EcsManager ecsManager;
  private final EcsConfiguration config;
  private final Map<String, Region> regionByCity;
  private final Set<String> knownRegions;
  private final Map<String, String> cityByRegion;
  private final Set<String> supportedServices;

  public EcsNodeService(EcsManager ecsManager,
                        EcsConfiguration ecsConfig,
                        AWSConfiguration awsConfig,
                        S3Configuration s3Config) {
    this.ecsManager = ecsManager;
    this.config = ecsConfig;
    this.regionByCity = awsConfig.getRegionCities()
        .entrySet()
        .stream()
        .collect(Collectors.toMap(Entry::getValue,
            entry -> Region.of(entry.getKey())));
    this.cityByRegion = awsConfig.getRegionCities();
    this.knownRegions = awsConfig.getRegionCities().keySet();
    this.supportedServices = s3Config.getServiceConfigs().keySet();
  }

  @Override
  public boolean isRegionValid(String userRegion) {
    if (knownRegions.contains(userRegion)) {
      return true;
    }

    return regionByCity.containsKey(userRegion);
  }

  @Override
  public boolean isRegionSupported(String userRegion, String serviceName) {
    Region region = regionByCity.getOrDefault(userRegion, Region.of(userRegion));
    return ecsManager.getSupportedRegions(serviceName).contains(region.id());
  }

  @Override
  public boolean isHostnameAvailable(String userRegion, String userHostName, String serviceName) {
    if (StringUtils.isBlank(userHostName)) {
      return false;
    }

    Map<String, String> matchingTags = Map.of(
        config.getHostNameTag(), userHostName,
        config.getServiceNameTag(), serviceName
    );
    return ecsManager.listTasks(matchingTags).isEmpty();
  }

  @Override
  public TaskInfo runNode(String userRegion,
                          String userTgId,
                          String serviceName,
                          List<String> additionalArgs) {

    Region region = regionByCity.getOrDefault(userRegion, Region.of(userRegion));

    String hostName = chooseHostName(userTgId, region.id(), serviceName);

    Map<String, String> assignedTags = Map.of(
        config.getHostNameTag(), hostName,
        config.getRunByTag(), userTgId,
        config.getServiceNameTag(), serviceName
    );

    return ecsManager.startTask(region, hostName, serviceName, assignedTags, additionalArgs);
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

  @Override
  public List<TaskInfo> listTasks(String userTgId) {
    // Create a stream of tasks for each service type
    Map<String, String> matchingTags = new HashMap<>();
    if (userTgId != null) {
      matchingTags.put(config.getRunByTag(), userTgId);
    }

    return ecsManager.listTasks(matchingTags);
  }

  @Override
  public List<SupportedRegionDescription> getSupportedRegionDescriptions() {
    Map<String, Set<String>> servicesByRegion = new LinkedHashMap<>();

    for (String serviceName : supportedServices) {
      Set<String> supportedRegions = ecsManager.getSupportedRegions(serviceName);
      for (String region : supportedRegions) {
        servicesByRegion
            .computeIfAbsent(region, ignored -> new LinkedHashSet<>())
            .add(serviceName);
      }
    }

    return servicesByRegion.entrySet().stream()
        .filter(entry -> !entry.getValue().isEmpty())
        .map(
            entry -> new SupportedRegionDescription(
                this.cityByRegion.get(entry.getKey()),
                entry.getKey(),
                new ArrayList<>(entry.getValue())))
        .toList();
  }

  @Override
  public List<String> getSupportedServices() {
    return new ArrayList<>(supportedServices);
  }

  private String chooseHostName(String userTgId, String regionId, String serviceName) {
    Set<String> hostNames = ecsManager.listTasks(Map.of(
            config.getServiceNameTag(), serviceName
        ))
        .stream()
        .map(TaskInfo::getHostName)
        .collect(Collectors.toSet());

    String suggestedHostName;
    int nodeNumber = 1;
    do {
      suggestedHostName = suggestHostName(userTgId, regionId, serviceName, nodeNumber);
      nodeNumber++;
    } while (hostNames.contains(suggestedHostName));

    return suggestedHostName;
  }

  private String suggestHostName(String userTgId, String regionId, String serviceName,
                                 int nodeNumber) {
    String city = cityByRegion.get(regionId);
    String servicePrefix = serviceName.toLowerCase();
    return sanitizeHostName("%s-%s-%s-%d".formatted(servicePrefix, userTgId, city, nodeNumber));
  }

  static String sanitizeHostName(String hostName) {
    return hostName.toLowerCase().replaceAll("[_.\\s]", "");
  }

}