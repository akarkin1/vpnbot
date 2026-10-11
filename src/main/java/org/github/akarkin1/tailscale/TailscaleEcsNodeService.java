package org.github.akarkin1.tailscale;

import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.config.YamlApplicationConfiguration.AWSConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.EcsConfiguration;
import org.github.akarkin1.ecs.EcsManager;
import org.github.akarkin1.ecs.EcsNode;
import software.amazon.awssdk.regions.Region;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class TailscaleEcsNodeService implements TailscaleNodeService {

  private final EcsManager ecsManager;
  private final EcsConfiguration config;
  private final AWSConfiguration awsConfig;

  @Override
  public boolean isRegionValid(String userRegion) {
    if (cityByRegion().containsKey(userRegion)) {
      return true;
    }

    return regionOfCity(userRegion).isPresent();
  }

  @Override
  public boolean isRegionSupported(String userRegion) {
    Region region = regionOf(userRegion);
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
  public EcsNode runNode(String userRegion, NodeOrigin origin, String userHostName,
                         Map<String, String> environment) {
    String userTgId = String.valueOf(origin.userId());
    Region region = regionOf(userRegion);

    String hostName = userHostName;
    if (userHostName == null) {
      String hostNamePrefix = StringUtils.isNotBlank(origin.username()) ? origin.username() : userTgId;
      hostName = chooseHostName(hostNamePrefix, region.id());
    }

    Map<String, String> assignedTags = new LinkedHashMap<>();
    assignedTags.put(config.getHostNameTag(), hostName);
    assignedTags.put(config.getRunByTag(), userTgId);
    if (StringUtils.isNotBlank(origin.username())) {
      assignedTags.put(config.getRunByNameTag(), origin.username());
    }
    if (origin.chatId() != null) {
      assignedTags.put(config.getChatIdTag(), String.valueOf(origin.chatId()));
    }
    if (origin.languageCode() != null) {
      assignedTags.put(config.getLanguageTag(), origin.languageCode());
    }
    assignedTags.put(config.getServiceNameTag(), config.getServiceName());
    return ecsManager.startTask(region, hostName, assignedTags, environment);
  }

  @Override
  public Optional<EcsNode> getNode(String regionId, String taskId) {
    return ecsManager.getTask(Region.of(regionId), taskId);
  }

  @Override
  public void stopNode(String regionId, String taskId, String reason) {
    ecsManager.stopTask(Region.of(regionId), taskId, reason);
  }

  @Override
  public String toRegionId(String userRegion) {
    return regionOf(userRegion).id();
  }

  private String chooseHostName(String hostNamePrefix, String regionId) {
    Set<String> hostNames = ecsManager.listTasks(Map.of(
            config.getServiceNameTag(), config.getServiceName()
        ))
        .stream()
        .map(EcsNode::getHostName)
        .collect(Collectors.toSet());

    String suggestedHostName;
    int nodeNumber = 1;
    do {
      suggestedHostName = suggestHostName(hostNamePrefix, regionId, nodeNumber);
      nodeNumber++;
    } while (hostNames.contains(suggestedHostName));

    return suggestedHostName;
  }

  private String suggestHostName(String hostNamePrefix, String regionId, int nodeNumber) {
    String city = cityByRegion().get(regionId);
    return sanitizeHostName("%s-%s-%d".formatted(hostNamePrefix, city, nodeNumber));
  }

  static String sanitizeHostName(String hostName) {
    return hostName.toLowerCase().replaceAll("[_.\\s]", "");
  }

  @Override
  public List<EcsNode> listNodes(String userId) {
    Map<String, String> matchingTags = new HashMap<>();
    matchingTags.put(config.getServiceNameTag(), config.getServiceName());
    if (userId != null) {
      matchingTags.put(config.getRunByTag(), userId);
    }

    return ecsManager.listTasks(matchingTags);
  }

  @Override
  public List<String> getSupportedRegionDescriptions() {
    return ecsManager.getSupportedRegions()
        .stream()
        .map(region -> "%s (%s)".formatted(cityByRegion().get(region), region))
        .toList();
  }

  @Override
  public Set<String> getSupportedRegionIds() {
    return ecsManager.getSupportedRegions();
  }

  private Map<String, String> cityByRegion() {
    return awsConfig.getRegionCities();
  }

  /** The region named by its id or by its city. */
  private Region regionOf(String userRegion) {
    return regionOfCity(userRegion).orElseGet(() -> Region.of(userRegion));
  }

  private Optional<Region> regionOfCity(String city) {
    return cityByRegion().entrySet()
        .stream()
        .filter(entry -> entry.getValue().equals(city))
        .map(entry -> Region.of(entry.getKey()))
        .findFirst();
  }
}
