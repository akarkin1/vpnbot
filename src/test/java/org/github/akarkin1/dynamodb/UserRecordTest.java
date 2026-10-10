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

class UserRecordTest {

  private static final TableSchema<UserRecord> SCHEMA = TableSchema.fromBean(UserRecord.class);

  @Test
  @DisplayName("3 AC-A13: keys are pk (partition) and sk (sort)")
  void keys() {
    assertEquals("pk", SCHEMA.tableMetadata().primaryPartitionKey());
    assertEquals(Optional.of("sk"), SCHEMA.tableMetadata().primarySortKey());
  }

  @Test
  @DisplayName("3 AC-A13: a user record maps to pk, sk and permissions as a String Set")
  void itemToMap() {
    Map<String, AttributeValue> item = SCHEMA.itemToMap(record(), true);

    assertEquals(Set.of("pk", "sk", "permissions"), item.keySet());
    assertEquals(AttributeValue.fromS("USER"), item.get("pk"));
    assertEquals(AttributeValue.fromS("Some_User"), item.get("sk"));
    AttributeValue permissions = item.get("permissions");
    assertEquals(AttributeValue.Type.SS, permissions.type());
    assertEquals(Set.of("RUN_NODES", "LIST_NODES"), new HashSet<>(permissions.ss()));
  }

  @Test
  @DisplayName("3 AC-A13: a stored user item maps back to an equal record")
  void roundTrip() {
    UserRecord record = record();

    UserRecord restored = SCHEMA.mapToItem(SCHEMA.itemToMap(record, true));

    assertEquals("USER", restored.getType());
    assertEquals("Some_User", restored.getUsername());
    assertEquals(Set.of("RUN_NODES", "LIST_NODES"), restored.getPermissions());
  }

  @Test
  @DisplayName("3 AC-A13: a new record has the type USER")
  void defaultType() {
    assertEquals("USER", UserRecord.TYPE);
    assertEquals(UserRecord.TYPE, new UserRecord().getType());
  }

  private static UserRecord record() {
    UserRecord record = new UserRecord();
    record.setUsername("Some_User");
    record.setPermissions(Set.of("RUN_NODES", "LIST_NODES"));
    return record;
  }

}
