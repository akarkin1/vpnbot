package org.github.akarkin1.ec2;



import org.github.akarkin1.config.ConfigManager;
import software.amazon.awssdk.regions.Region;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class EnvBasedRegionService implements RegionService {

  public static final EnvBasedRegionService INSTANCE = new EnvBasedRegionService();

  @Override
  public List<Region> getUsedRegions() {
    Map<String, Region> allRegions = Region.regions()
        .stream()
        .collect(Collectors.toMap(Region::id, Function.identity()));

    return ConfigManager.getUsedRegions()
        .stream()
        .filter(allRegions::containsKey)
        .map(allRegions::get)
        .toList();
  }
}
