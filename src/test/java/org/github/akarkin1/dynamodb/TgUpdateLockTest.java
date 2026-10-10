package org.github.akarkin1.dynamodb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TgUpdateLockTest {

  private static final TableSchema<TgUpdateLock> SCHEMA = TableSchema.fromBean(TgUpdateLock.class);

  @Test
  @DisplayName("3 AC-A13: keys are pk (partition) and sk (sort)")
  void keys() {
    assertEquals("pk", SCHEMA.tableMetadata().primaryPartitionKey());
    assertEquals(Optional.of("sk"), SCHEMA.tableMetadata().primarySortKey());
  }

  @Test
  @DisplayName("3 AC-A13: a lock maps to pk, sk and the numbers expiresAt and receivedAt")
  void itemToMap() {
    Map<String, AttributeValue> item = SCHEMA.itemToMap(record(), true);

    assertEquals(Map.of(
        "pk", AttributeValue.fromS("TG_UPDATE_LOCK"),
        "sk", AttributeValue.fromS("123456789"),
        "expiresAt", AttributeValue.fromN("1760184000"),
        "receivedAt", AttributeValue.fromN("1760097600123")), item);
  }

  @Test
  @DisplayName("3 AC-A13: a stored lock maps back to an equal record")
  void roundTrip() {
    TgUpdateLock record = record();

    assertEquals(record, SCHEMA.mapToItem(SCHEMA.itemToMap(record, true)));
  }

  @Test
  @DisplayName("3 AC-A13: a new lock has the type TG_UPDATE_LOCK")
  void defaultType() {
    assertEquals("TG_UPDATE_LOCK", TgUpdateLock.TYPE);
    assertEquals(TgUpdateLock.TYPE, new TgUpdateLock().getType());
  }

  private static TgUpdateLock record() {
    TgUpdateLock record = new TgUpdateLock();
    record.setUpdateId("123456789");
    record.setExpiresAt(1_760_184_000L);
    record.setReceivedAt(1_760_097_600_123L);
    return record;
  }

}
