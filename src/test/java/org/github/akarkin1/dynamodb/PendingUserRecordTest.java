package org.github.akarkin1.dynamodb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PendingUserRecordTest {

  private static final TableSchema<PendingUserRecord> SCHEMA =
      TableSchema.fromBean(PendingUserRecord.class);

  @Test
  @DisplayName("4 §5: keys are pk (partition) and sk (sort)")
  void keys() {
    assertEquals("pk", SCHEMA.tableMetadata().primaryPartitionKey());
    assertEquals(Optional.of("sk"), SCHEMA.tableMetadata().primarySortKey());
  }

  @Test
  @DisplayName("4 §5: a pending user maps to pk PENDING_USER, sk username and permissions as a String Set")
  void itemToMap() {
    Map<String, AttributeValue> item = SCHEMA.itemToMap(record(), true);

    assertEquals(Set.of("pk", "sk", "permissions"), item.keySet());
    assertEquals(AttributeValue.fromS("PENDING_USER"), item.get("pk"));
    assertEquals(AttributeValue.fromS("Some_User"), item.get("sk"));
    AttributeValue permissions = item.get("permissions");
    assertEquals(AttributeValue.Type.SS, permissions.type());
    assertEquals(Set.of("RUN_NODES", "LIST_NODES"), new HashSet<>(permissions.ss()));
  }

  @Test
  @DisplayName("4 §5: a stored pending user item maps back to an equal record")
  void roundTrip() {
    PendingUserRecord restored = SCHEMA.mapToItem(SCHEMA.itemToMap(record(), true));

    assertEquals("PENDING_USER", restored.getType());
    assertEquals("Some_User", restored.getUsername());
    assertEquals(Set.of("RUN_NODES", "LIST_NODES"), restored.getPermissions());
  }

  @Test
  @DisplayName("4 §5: a new record has the type PENDING_USER")
  void defaultType() {
    assertEquals("PENDING_USER", PendingUserRecord.TYPE);
    assertEquals(PendingUserRecord.TYPE, new PendingUserRecord().getType());
  }

  private static PendingUserRecord record() {
    PendingUserRecord record = new PendingUserRecord();
    record.setUsername("Some_User");
    record.setPermissions(Set.of("RUN_NODES", "LIST_NODES"));
    return record;
  }

}
