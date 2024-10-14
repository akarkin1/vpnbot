package org.github.akarkin1.ec2;


import software.amazon.awssdk.regions.Region;

import java.util.List;

public interface RegionService {

  List<Region> getUsedRegions();

}
