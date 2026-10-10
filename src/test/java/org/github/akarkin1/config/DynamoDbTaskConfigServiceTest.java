package org.github.akarkin1.config;

import org.github.akarkin1.dynamodb.FakeTable;
import org.github.akarkin1.dynamodb.RegionRecord;
import org.github.akarkin1.metrics.MetricComponent;
import org.github.akarkin1.metrics.RecordingRequestMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamoDbTaskConfigServiceTest {

  private final RecordingRequestMetrics metrics = new RecordingRequestMetrics();
  private final FakeTable<RegionRecord> regions =
      FakeTable.of(RegionRecord.class, () -> metrics.isTiming(MetricComponent.DYNAMODB));

  private DynamoDbTaskConfigService service;

  @BeforeEach
  void setUp() {
    service = new DynamoDbTaskConfigService(regions.table(), metrics);
  }

  @Test
  @DisplayName("3 AC-A1: supported regions are read with a query pk = REGION across all pages")
  void supportedRegionsAcrossPages() {
    regions.withPages(List.of(region("eu-central-1"), region("eu-west-1")),
                      List.of(region("us-east-1")));

    List<Region> result = service.getSupportedRegions();

    assertEquals(List.of(Region.EU_CENTRAL_1, Region.EU_WEST_1, Region.US_EAST_1), result);
    assertEquals(1, regions.queries().size());
    assertEquals(Map.of("pk", AttributeValue.fromS("REGION")),
                 regions.keyCondition(regions.queries().getFirst()));
  }

  @Test
  @DisplayName("3 AC-A1: region ids unknown to the AWS SDK are skipped")
  void unknownRegionsSkipped() {
    regions.withPages(List.of(region("eu-west-1"), region("mars-north-1")),
                      List.of(region("ap-southeast-7")));

    assertEquals(List.of(Region.EU_WEST_1, Region.of("ap-southeast-7")),
                 service.getSupportedRegions());
  }

  @Test
  @DisplayName("3 AC-A1: an empty table means no supported regions")
  void emptyTable() {
    regions.withPages(List.of());

    assertTrue(service.getSupportedRegions().isEmpty());
  }

  @Test
  @DisplayName("3 AC-A1: no pages at all means no supported regions")
  void noPages() {
    regions.withPages();

    assertTrue(service.getSupportedRegions().isEmpty());
  }

  @Test
  @DisplayName("3 AC-A2: runtime parameters are read with GetItem (REGION, region id) and mapped")
  void runtimeParameters() {
    regions.withItem(region("eu-west-1"));

    TaskRuntimeParameters params = service.getTaskRuntimeParameters(Region.EU_WEST_1);

    assertEquals(List.of(FakeTable.key("REGION", "eu-west-1")), regions.gets());
    assertEquals("cluster-eu-west-1", params.getEcsClusterName());
    assertEquals("arn:aws:ecs:eu-west-1:123:task-definition/vpn:7", params.getEcsTaskDefinition());
    assertEquals("subnet-eu-west-1", params.getSubnetId());
    assertEquals("sg-eu-west-1", params.getSecurityGroupId());
  }

  @Test
  @DisplayName("3 AC-A2: a region without an item is not configured")
  void missingItem() {
    IllegalStateException e = assertThrows(IllegalStateException.class,
        () -> service.getTaskRuntimeParameters(Region.EU_WEST_1));

    assertEquals("Region eu-west-1 is not configured", e.getMessage());
  }

  @Test
  @DisplayName("3 AC-A2: a region item without one of the four attributes is not configured")
  void missingAttribute() {
    List<Consumer<RegionRecord>> removals = List.of(
        r -> r.setEcsClusterName(null),
        r -> r.setEcsTaskDefinitionArn(null),
        r -> r.setSubnetId(null),
        r -> r.setSecurityGroupId(null));

    for (int i = 0; i < removals.size(); i++) {
      RegionRecord record = region("eu-west-1");
      removals.get(i).accept(record);
      FakeTable<RegionRecord> table =
          FakeTable.of(RegionRecord.class, () -> true).withItem(record);
      DynamoDbTaskConfigService incomplete = new DynamoDbTaskConfigService(table.table(), metrics);

      IllegalStateException e = assertThrows(IllegalStateException.class,
          () -> incomplete.getTaskRuntimeParameters(Region.EU_WEST_1), "removal #" + i);
      assertEquals("Region eu-west-1 is not configured", e.getMessage(), "removal #" + i);
    }
  }

  @Test
  @DisplayName("3 AC-A7: the query and every page fetch run inside a DYNAMODB timing")
  void queryTimed() {
    regions.withPages(List.of(region("eu-central-1")), List.of(region("us-east-1")));

    service.getSupportedRegions();

    assertTimedAsDynamoDb();
  }

  @Test
  @DisplayName("3 AC-A7: GetItem runs inside a DYNAMODB timing")
  void getItemTimed() {
    regions.withItem(region("eu-west-1"));

    service.getTaskRuntimeParameters(Region.EU_WEST_1);

    assertTimedAsDynamoDb();
  }

  private void assertTimedAsDynamoDb() {
    assertFalse(regions.timedCalls().isEmpty());
    assertFalse(regions.timedCalls().contains(false),
                "DynamoDB call outside a timing: " + regions.timedCalls());
    assertFalse(metrics.timed().isEmpty());
    assertTrue(metrics.timed().stream().allMatch(MetricComponent.DYNAMODB::equals),
               metrics.timed().toString());
  }

  private static RegionRecord region(String id) {
    RegionRecord record = new RegionRecord();
    record.setRegionId(id);
    record.setEcsClusterName("cluster-" + id);
    record.setEcsTaskDefinitionArn("arn:aws:ecs:" + id + ":123:task-definition/vpn:7");
    record.setSubnetId("subnet-" + id);
    record.setSecurityGroupId("sg-" + id);
    record.setUpdatedAt("2026-10-10T12:00:00Z");
    return record;
  }

}
