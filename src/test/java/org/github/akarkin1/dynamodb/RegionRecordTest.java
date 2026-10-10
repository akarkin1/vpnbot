package org.github.akarkin1.dynamodb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegionRecordTest {

  private static final TableSchema<RegionRecord> SCHEMA = TableSchema.fromBean(RegionRecord.class);

  @Test
  @DisplayName("3 AC-A13: keys are pk (partition) and sk (sort)")
  void keys() {
    assertEquals("pk", SCHEMA.tableMetadata().primaryPartitionKey());
    assertEquals(Optional.of("sk"), SCHEMA.tableMetadata().primarySortKey());
  }

  @Test
  @DisplayName("3 AC-A13: a region record maps to exactly the attributes of the data model")
  void itemToMap() {
    Map<String, AttributeValue> item = SCHEMA.itemToMap(record(), true);

    assertEquals(Map.of(
        "pk", AttributeValue.fromS("REGION"),
        "sk", AttributeValue.fromS("eu-central-1"),
        "ecsClusterName", AttributeValue.fromS("vpn-cluster"),
        "ecsTaskDefinitionArn",
        AttributeValue.fromS("arn:aws:ecs:eu-central-1:123456789012:task-definition/vpn:3"),
        "subnetId", AttributeValue.fromS("subnet-0abc"),
        "securityGroupId", AttributeValue.fromS("sg-0def"),
        "updatedAt", AttributeValue.fromS("2026-10-10T12:00:00Z")), item);
  }

  @Test
  @DisplayName("3 AC-A13: a stored region item maps back to an equal record")
  void roundTrip() {
    RegionRecord record = record();

    assertEquals(record, SCHEMA.mapToItem(SCHEMA.itemToMap(record, true)));
  }

  @Test
  @DisplayName("3 AC-A13: a new record has the type REGION")
  void defaultType() {
    assertEquals("REGION", RegionRecord.TYPE);
    assertEquals(RegionRecord.TYPE, new RegionRecord().getType());
  }

  private static RegionRecord record() {
    RegionRecord record = new RegionRecord();
    record.setRegionId("eu-central-1");
    record.setEcsClusterName("vpn-cluster");
    record.setEcsTaskDefinitionArn("arn:aws:ecs:eu-central-1:123456789012:task-definition/vpn:3");
    record.setSubnetId("subnet-0abc");
    record.setSecurityGroupId("sg-0def");
    record.setUpdatedAt("2026-10-10T12:00:00Z");
    return record;
  }

}
