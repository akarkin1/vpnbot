package org.github.akarkin1.dynamodb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class RegionRequestRecordTest {

  private static final TableSchema<RegionRequestRecord> SCHEMA =
      TableSchema.fromBean(RegionRequestRecord.class);

  @Test
  @DisplayName("5 §5: keys are pk (partition) and sk (sort)")
  void keys() {
    assertEquals("pk", SCHEMA.tableMetadata().primaryPartitionKey());
    assertEquals(Optional.of("sk"), SCHEMA.tableMetadata().primarySortKey());
  }

  @Test
  @DisplayName("5 AC-5, §5: a request maps to pk REGION_REQUEST, sk region id, the strings and the numbers userId, requestedAt and expiresAt")
  void itemToMap() {
    Map<String, AttributeValue> item = SCHEMA.itemToMap(record(), true);

    assertEquals(Map.of(
        "pk", AttributeValue.fromS("REGION_REQUEST"),
        "sk", AttributeValue.fromS("ap-southeast-2"),
        "userId", AttributeValue.fromN("123456789"),
        "username", AttributeValue.fromS("Some_User"),
        "firstName", AttributeValue.fromS("Alex"),
        "lastName", AttributeValue.fromS("Smith"),
        "languageCode", AttributeValue.fromS("ru"),
        "requestedAt", AttributeValue.fromN("1760097600123"),
        "expiresAt", AttributeValue.fromN("1762689600")), item);
  }

  @Test
  @DisplayName("5 AC-5, §5: a request without a username or a last name has neither attribute")
  void nullsOmitted() {
    RegionRequestRecord record = record();
    record.setUsername(null);
    record.setLastName(null);

    Map<String, AttributeValue> item = SCHEMA.itemToMap(record, true);

    assertEquals(Set.of("pk", "sk", "userId", "firstName", "languageCode", "requestedAt",
                        "expiresAt"),
                 item.keySet());
    assertNull(SCHEMA.mapToItem(item).getUsername());
    assertNull(SCHEMA.mapToItem(item).getLastName());
  }

  @Test
  @DisplayName("5 AC-5: a stored request maps back to an equal record")
  void roundTrip() {
    RegionRequestRecord record = record();

    RegionRequestRecord restored = SCHEMA.mapToItem(SCHEMA.itemToMap(record, true));

    assertEquals(record, restored);
    assertEquals("REGION_REQUEST", restored.getType());
    assertEquals("ap-southeast-2", restored.getRegionId());
    assertEquals(123_456_789L, restored.getUserId());
    assertEquals("Some_User", restored.getUsername());
    assertEquals("Alex", restored.getFirstName());
    assertEquals("Smith", restored.getLastName());
    assertEquals("ru", restored.getLanguageCode());
    assertEquals(1_760_097_600_123L, restored.getRequestedAt());
    assertEquals(1_762_689_600L, restored.getExpiresAt());
  }

  @Test
  @DisplayName("5 §5: the record stores expiresAt as set – it does not derive it from requestedAt (the service does)")
  void expiresAtStoredAsSet() {
    RegionRequestRecord record = record();
    record.setExpiresAt(42L);

    Map<String, AttributeValue> item = SCHEMA.itemToMap(record, true);

    assertEquals(AttributeValue.fromN("42"), item.get("expiresAt"));
    assertEquals(42L, SCHEMA.mapToItem(item).getExpiresAt());
  }

  @Test
  @DisplayName("5 §5: a new record has the type REGION_REQUEST")
  void defaultType() {
    assertEquals("REGION_REQUEST", RegionRequestRecord.TYPE);
    assertEquals(RegionRequestRecord.TYPE, new RegionRequestRecord().getType());
  }

  /** requestedAt 2025-10-10T12:00:00.123Z (epoch ms), expiresAt 30 days later (epoch seconds). */
  private static RegionRequestRecord record() {
    RegionRequestRecord record = new RegionRequestRecord();
    record.setRegionId("ap-southeast-2");
    record.setUserId(123_456_789L);
    record.setUsername("Some_User");
    record.setFirstName("Alex");
    record.setLastName("Smith");
    record.setLanguageCode("ru");
    record.setRequestedAt(1_760_097_600_123L);
    record.setExpiresAt(1_762_689_600L);
    return record;
  }

}
