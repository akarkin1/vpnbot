package org.github.akarkin1.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.dynamodb.RegionRecord;
import org.github.akarkin1.metrics.MetricComponent;
import org.github.akarkin1.metrics.RequestMetrics;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.regions.Region;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Reads the supported regions and their stack outputs from the {@code REGION} records. */
@Log4j2
@RequiredArgsConstructor
public class DynamoDbTaskConfigService implements TaskConfigService {

  private static final List<Region> AWS_REGIONS = new ArrayList<>(Region.regions());
  static {
    AWS_REGIONS.add(Region.of("ap-southeast-7"));
  }

  private static final Map<String, Region> KNOWN_REGIONS = AWS_REGIONS
      .stream()
      .collect(Collectors.toMap(Region::id, r -> r));

  private final DynamoDbTable<RegionRecord> regions;
  private final RequestMetrics metrics;

  @Override
  public List<Region> getSupportedRegions() {
    QueryConditional allRegions = QueryConditional.keyEqualTo(
        Key.builder().partitionValue(RegionRecord.TYPE).build());
    List<RegionRecord> records = metrics.time(MetricComponent.DYNAMODB,
                                              () -> regions.query(allRegions).items()
                                                  .stream()
                                                  .toList());
    return records.stream()
        .map(RegionRecord::getRegionId)
        .flatMap(regionId -> {
          if (!KNOWN_REGIONS.containsKey(regionId)) {
            log.warn("Region id {} is not known to AWS SDK", regionId);
            return Stream.empty();
          }
          return Stream.of(KNOWN_REGIONS.get(regionId));
        })
        .toList();
  }

  @Override
  public TaskRuntimeParameters getTaskRuntimeParameters(Region region) {
    Key key = Key.builder()
        .partitionValue(RegionRecord.TYPE)
        .sortValue(region.id())
        .build();
    RegionRecord record = metrics.time(MetricComponent.DYNAMODB, () -> regions.getItem(key));
    if (record == null
        || StringUtils.isAnyBlank(record.getEcsClusterName(), record.getEcsTaskDefinitionArn(),
                                  record.getSubnetId(), record.getSecurityGroupId())) {
      throw new IllegalStateException("Region %s is not configured".formatted(region.id()));
    }

    return TaskRuntimeParameters.builder()
        .ecsClusterName(record.getEcsClusterName())
        .ecsTaskDefinition(record.getEcsTaskDefinitionArn())
        .subnetId(record.getSubnetId())
        .securityGroupId(record.getSecurityGroupId())
        .build();
  }

}
