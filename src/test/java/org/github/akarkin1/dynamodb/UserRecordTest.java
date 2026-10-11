package org.github.akarkin1.dynamodb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 4: the {@code USER} record is keyed by the user id (sk, a String like every sort key of the
 * table) and carries a nullable {@code username} attribute. Checked through attribute maps, so the
 * test does not depend on the bean's property names.
 */
class UserRecordTest {

  private static final TableSchema<UserRecord> SCHEMA = TableSchema.fromBean(UserRecord.class);

  @Test
  @DisplayName("3 AC-A13: keys are pk (partition) and sk (sort)")
  void keys() {
    assertEquals("pk", SCHEMA.tableMetadata().primaryPartitionKey());
    assertEquals(Optional.of("sk"), SCHEMA.tableMetadata().primarySortKey());
  }

  @Test
  @DisplayName("4 §5: a stored USER item (sk = user id, username, permissions SS) round-trips through the bean unchanged")
  void roundTrip() {
    Map<String, AttributeValue> item = Map.of(
        "pk", AttributeValue.fromS("USER"),
        "sk", AttributeValue.fromS("123456789"),
        "username", AttributeValue.fromS("Some_User"),
        "permissions", AttributeValue.fromSs(List.of("RUN_NODES", "LIST_NODES")));

    Map<String, AttributeValue> restored = SCHEMA.itemToMap(SCHEMA.mapToItem(item), true);

    assertEquals(Set.of("pk", "sk", "username", "permissions"), restored.keySet());
    assertEquals(AttributeValue.fromS("USER"), restored.get("pk"));
    assertEquals(AttributeValue.fromS("123456789"), restored.get("sk"));
    assertEquals(AttributeValue.fromS("Some_User"), restored.get("username"));
    assertEquals(AttributeValue.Type.SS, restored.get("permissions").type());
    assertEquals(Set.of("RUN_NODES", "LIST_NODES"), new HashSet<>(restored.get("permissions").ss()));
  }

  @Test
  @DisplayName("4 §5: the username attribute is nullable: an item without it round-trips without it")
  void roundTripWithoutUsername() {
    Map<String, AttributeValue> item = Map.of(
        "pk", AttributeValue.fromS("USER"),
        "sk", AttributeValue.fromS("123456789"),
        "permissions", AttributeValue.fromSs(List.of("LIST_NODES")));

    Map<String, AttributeValue> restored = SCHEMA.itemToMap(SCHEMA.mapToItem(item), true);

    assertEquals(item, restored);
  }

  @Test
  @DisplayName("3 AC-A13: a new record has the type USER")
  void defaultType() {
    assertEquals("USER", UserRecord.TYPE);
    assertEquals(UserRecord.TYPE, new UserRecord().getType());
    assertEquals(AttributeValue.fromS("USER"), SCHEMA.itemToMap(new UserRecord(), true).get("pk"));
  }

}
