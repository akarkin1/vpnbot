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

class AccessRequestRecordTest {

  private static final TableSchema<AccessRequestRecord> SCHEMA =
      TableSchema.fromBean(AccessRequestRecord.class);

  @Test
  @DisplayName("4 §5: keys are pk (partition) and sk (sort)")
  void keys() {
    assertEquals("pk", SCHEMA.tableMetadata().primaryPartitionKey());
    assertEquals(Optional.of("sk"), SCHEMA.tableMetadata().primarySortKey());
  }

  @Test
  @DisplayName("4 §5: a request maps to pk ACCESS_REQUEST, sk user id, the strings and the numbers requestedAt and expiresAt")
  void itemToMap() {
    Map<String, AttributeValue> item = SCHEMA.itemToMap(record(), true);

    assertEquals(Map.of(
        "pk", AttributeValue.fromS("ACCESS_REQUEST"),
        "sk", AttributeValue.fromS("123456789"),
        "username", AttributeValue.fromS("Some_User"),
        "firstName", AttributeValue.fromS("Alex"),
        "languageCode", AttributeValue.fromS("ru"),
        "requestedAt", AttributeValue.fromN("1760097600123"),
        "expiresAt", AttributeValue.fromN("1760702400")), item);
  }

  @Test
  @DisplayName("4 §5: a request without a username has no username attribute")
  void nullUsernameOmitted() {
    AccessRequestRecord record = record();
    record.setUsername(null);

    Map<String, AttributeValue> item = SCHEMA.itemToMap(record, true);

    assertEquals(Set.of("pk", "sk", "firstName", "languageCode", "requestedAt", "expiresAt"),
                 item.keySet());
    assertNull(SCHEMA.mapToItem(item).getUsername());
  }

  @Test
  @DisplayName("4 §5: a stored request maps back to an equal record")
  void roundTrip() {
    AccessRequestRecord record = record();

    AccessRequestRecord restored = SCHEMA.mapToItem(SCHEMA.itemToMap(record, true));

    assertEquals(record, restored);
    assertEquals("ACCESS_REQUEST", restored.getType());
    assertEquals("123456789", restored.getUserId());
    assertEquals("Some_User", restored.getUsername());
    assertEquals("Alex", restored.getFirstName());
    assertEquals("ru", restored.getLanguageCode());
    assertEquals(1_760_097_600_123L, restored.getRequestedAt());
    assertEquals(1_760_702_400L, restored.getExpiresAt());
  }

  @Test
  @DisplayName("4 §5: a new record has the type ACCESS_REQUEST")
  void defaultType() {
    assertEquals("ACCESS_REQUEST", AccessRequestRecord.TYPE);
    assertEquals(AccessRequestRecord.TYPE, new AccessRequestRecord().getType());
  }

  private static AccessRequestRecord record() {
    AccessRequestRecord record = new AccessRequestRecord();
    record.setUserId("123456789");
    record.setUsername("Some_User");
    record.setFirstName("Alex");
    record.setLanguageCode("ru");
    record.setRequestedAt(1_760_097_600_123L);
    record.setExpiresAt(1_760_702_400L);
    return record;
  }

}
