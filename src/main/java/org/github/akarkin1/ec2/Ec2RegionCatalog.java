package org.github.akarkin1.ec2;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.metrics.MetricComponent;
import org.github.akarkin1.metrics.RequestMetrics;
import software.amazon.awssdk.services.ec2.model.DescribeRegionsRequest;
import software.amazon.awssdk.services.ec2.model.Region;

import java.util.Set;
import java.util.stream.Collectors;

/**
 * The regions enabled for the account, from {@code ec2:DescribeRegions} without
 * {@code allRegions} (so an opt-in region appears only once opted in), called in the Lambda's own
 * region. Timed under {@link MetricComponent#ECS} like the other AWS calls. A failure propagates.
 */
@RequiredArgsConstructor
public class Ec2RegionCatalog implements RegionCatalog {

  private final Ec2ClientPool pool;
  private final String regionId;
  private final RequestMetrics metrics;

  @Override
  public Set<String> enabledRegions() {
    return metrics.time(MetricComponent.ECS, () -> pool.getForRegion(regionId)
        .describeRegions(DescribeRegionsRequest.builder().build())
        .regions().stream()
        .map(Region::regionName)
        .collect(Collectors.toUnmodifiableSet()));
  }

}
