package org.github.akarkin1.service;

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
import java.util.stream.Stream;

/**
 * Implementation of NodeService that uses ECS to run and manage nodes.
 */
public class EcsNodeService implements NodeService {

  private final EcsManager ecsManager;
  private final EcsConfiguration config;
  private final Map<String, Region> regionByCity;
  private final Set<String> knownRegions;
  private final Map<String, String> cityByRegion;
  private final List<ServiceType> supportedServiceTypes;

  public EcsNodeService(EcsManager ecsManager,
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
    this.supportedServiceTypes = List.of(ServiceType.values());
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

    // Check if hostname is available across all service types
    return supportedServiceTypes.stream()
        .allMatch(serviceType -> {
            Map<String, String> matchingTags = Map.of(
                config.getServiceNameTag(), serviceType.getServiceName(),
                config.getHostNameTag(), userHostName
            );
            return ecsManager.listTasks(matchingTags).isEmpty();
        });
  }

  @Override
  public TaskInfo runNode(String userRegion, String userTgId, String userHostName, ServiceType serviceType) {
    Region region = regionByCity.getOrDefault(userRegion, Region.of(userRegion));

    String hostName = userHostName;
    if (userHostName == null) {
      hostName = chooseHostName(userTgId, region.id(), serviceType);
    }

    Map<String, String> assignedTags = Map.of(
        config.getHostNameTag(), hostName,
        config.getRunByTag(), userTgId,
        config.getServiceNameTag(), serviceType.getServiceName()
    );
    
    TaskInfo taskInfo = ecsManager.startTask(region, hostName, assignedTags);
    return TaskInfo.builder()
        .id(taskInfo.getId())
        .hostName(taskInfo.getHostName())
        .state(taskInfo.getState())
        .cluster(taskInfo.getCluster())
        .publicIp(taskInfo.getPublicIp())
        .location(taskInfo.getLocation())
        .region(taskInfo.getRegion())
        .serviceType(serviceType)
        .build();
  }

  @Override
  public Optional<TaskInfo> getFullTaskInfo(Region region, String clusterName, String taskId) {
    return ecsManager.getFullTaskInfo(region, clusterName, taskId)
        .map(taskInfo -> {
            // Try to determine the service type from the task tags
            ServiceType serviceType = determineServiceType(taskInfo);
            return TaskInfo.builder()
                .id(taskInfo.getId())
                .hostName(taskInfo.getHostName())
                .state(taskInfo.getState())
                .cluster(taskInfo.getCluster())
                .publicIp(taskInfo.getPublicIp())
                .location(taskInfo.getLocation())
                .region(taskInfo.getRegion())
                .serviceType(serviceType)
                .build();
        });
  }

  @Override
  public RunTaskStatus checkNodeStatus(TaskInfo taskInfo) {
    return ecsManager.checkTaskHealth(taskInfo.getRegion(),
                                      taskInfo.getCluster(),
                                      taskInfo.getId());
  }

  private String chooseHostName(String userTgId, String regionId, ServiceType serviceType) {
    Set<String> hostNames = ecsManager.listTasks(Map.of(
            config.getServiceNameTag(), serviceType.getServiceName()
        ))
        .stream()
        .map(TaskInfo::getHostName)
        .collect(Collectors.toSet());

    String suggestedHostName;
    int nodeNumber = 1;
    do {
      suggestedHostName = suggestHostName(userTgId, regionId, serviceType, nodeNumber);
      nodeNumber++;
    } while (hostNames.contains(suggestedHostName));

    return suggestedHostName;
  }

  private String suggestHostName(String userTgId, String regionId, ServiceType serviceType, int nodeNumber) {
    String city = cityByRegion.get(regionId);
    String servicePrefix = serviceType.name().toLowerCase().substring(0, 3); // First 3 letters of service type
    return sanitizeHostName("%s-%s-%s-%d".formatted(userTgId, city, servicePrefix, nodeNumber));
  }

  static String sanitizeHostName(String hostName) {
    return hostName.toLowerCase().replaceAll("[_.\\s]", "");
  }

  @Override
  public List<TaskInfo> listTasks(String userTgId) {
    // Create a stream of tasks for each service type
    Stream<TaskInfo> allTasks = supportedServiceTypes.stream()
        .flatMap(serviceType -> {
            Map<String, String> matchingTags = new HashMap<>();
            matchingTags.put(config.getServiceNameTag(), serviceType.getServiceName());
            if (userTgId != null) {
                matchingTags.put(config.getRunByTag(), userTgId);
            }
            
            return ecsManager.listTasks(matchingTags).stream()
                .map(taskInfo -> TaskInfo.builder()
                    .id(taskInfo.getId())
                    .hostName(taskInfo.getHostName())
                    .state(taskInfo.getState())
                    .cluster(taskInfo.getCluster())
                    .publicIp(taskInfo.getPublicIp())
                    .location(taskInfo.getLocation())
                    .region(taskInfo.getRegion())
                    .serviceType(serviceType)
                    .build());
        });
    
    return allTasks.collect(Collectors.toList());
  }

  @Override
  public List<String> getSupportedRegionDescriptions() {
    return ecsManager.getSupportedRegions()
        .stream()
        .flatMap(region -> supportedServiceTypes.stream()
            .map(serviceType -> "%s (%s) - %s".formatted(
                this.cityByRegion.get(region), 
                region, 
                serviceType.getDisplayName())))
        .toList();
  }

  @Override
  public List<ServiceType> getSupportedServiceTypes() {
    return supportedServiceTypes;
  }

  /**
   * Determine the service type from a task info.
   *
   * @param taskInfo the task info
   * @return the service type, or null if not found
   */
  private ServiceType determineServiceType(TaskInfo taskInfo) {
    // This would normally extract the service type from the task tags,
    // but since we don't have access to the tags here, we'll have to
    // infer it from other information.
    
    // For now, we'll just return VPN as the default
    return ServiceType.VPN;
  }
}