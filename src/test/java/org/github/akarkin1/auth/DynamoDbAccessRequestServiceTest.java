package org.github.akarkin1.auth;

import org.github.akarkin1.dynamodb.AccessRequestRecord;
import org.github.akarkin1.dynamodb.FakeTable;
import org.github.akarkin1.metrics.MetricComponent;
import org.github.akarkin1.metrics.RecordingRequestMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamoDbAccessRequestServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-11T12:00:00.123Z");
  private static final long SEVEN_DAYS_SECONDS = Duration.ofDays(7).toSeconds();
  private static final long USER_ID = 123456789L;
  /** Requested "now", so the expected TTL is the same whether it is derived from the request or the clock. */
  private static final AccessRequest REQUEST =
      new AccessRequest(USER_ID, "Some_User", "Alex", "ru", NOW.toEpochMilli());

  private final RecordingRequestMetrics metrics = new RecordingRequestMetrics();
  private final FakeTable<AccessRequestRecord> table =
      FakeTable.of(AccessRequestRecord.class, () -> metrics.isTiming(MetricComponent.DYNAMODB));

  private DynamoDbAccessRequestService service;

  @BeforeEach
  void setUp() {
    service = new DynamoDbAccessRequestService(table.table(), Clock.fixed(NOW, ZoneOffset.UTC),
                                               metrics);
  }

  @Test
  @DisplayName("4 AC-4, §5: create puts ACCESS_REQUEST/<id> with the request's fields and expiresAt = requestedAt + 7 days (epoch seconds)")
  void createPuts() {
    service.create(REQUEST);

    assertEquals(1, table.puts().size());
    AccessRequestRecord item = table.puts().getFirst().item();
    Map<String, AttributeValue> stored = table.schema().itemToMap(item, true);
    assertEquals(Map.of(
        "pk", AttributeValue.fromS("ACCESS_REQUEST"),
        "sk", AttributeValue.fromS("123456789"),
        "username", AttributeValue.fromS("Some_User"),
        "firstName", AttributeValue.fromS("Alex"),
        "languageCode", AttributeValue.fromS("ru"),
        "requestedAt", AttributeValue.fromN(String.valueOf(NOW.toEpochMilli())),
        "expiresAt", AttributeValue.fromN(String.valueOf(NOW.getEpochSecond() + SEVEN_DAYS_SECONDS))),
                 stored);
  }

  @Test
  @DisplayName("4 §5: create of a user without a username writes no username attribute")
  void createWithoutUsername() {
    service.create(new AccessRequest(USER_ID, null, "Alex", "en", NOW.toEpochMilli()));

    AccessRequestRecord item = table.puts().getFirst().item();
    assertFalse(table.schema().itemToMap(item, true).containsKey("username"));
  }

  @Test
  @DisplayName("4 §5: find reads ACCESS_REQUEST/<id> and maps it to an AccessRequest")
  void findMapsRecord() {
    table.withItem(record(USER_ID, NOW.getEpochSecond() + SEVEN_DAYS_SECONDS));

    Optional<AccessRequest> found = service.find(USER_ID);

    assertEquals(Optional.of(REQUEST), found);
    assertEquals(List.of(FakeTable.key("ACCESS_REQUEST", "123456789")), table.gets());
  }

  @Test
  @DisplayName("4 §5: find of an unknown id is empty")
  void findMissing() {
    assertEquals(Optional.empty(), service.find(42L));
    assertEquals(List.of(FakeTable.key("ACCESS_REQUEST", "42")), table.gets());
  }

  @Test
  @DisplayName("4 D-6: find ignores an expired request that TTL has not removed yet (the user can ask again)")
  void findIgnoresExpired() {
    table.withItem(record(USER_ID, NOW.getEpochSecond() - 1));

    assertEquals(Optional.empty(), service.find(USER_ID));
  }

  @Test
  @DisplayName("4 §5: delete deletes ACCESS_REQUEST/<id>")
  void deleteDeletes() {
    service.delete(USER_ID);

    assertEquals(List.of(FakeTable.key("ACCESS_REQUEST", "123456789")), table.deletes());
    assertEquals(List.of(), table.puts());
  }

  @Test
  @DisplayName("4 §5: every DynamoDB call runs inside a DYNAMODB timing")
  void callsTimed() {
    table.withItem(record(USER_ID, NOW.getEpochSecond() + SEVEN_DAYS_SECONDS));

    service.find(USER_ID);
    service.create(REQUEST);
    service.delete(USER_ID);

    assertEquals(3, table.timedCalls().size());
    assertTrue(table.timedCalls().stream().allMatch(Boolean::booleanValue),
               "calls outside a DYNAMODB timing: " + table.timedCalls());
  }

  private static AccessRequestRecord record(long userId, long expiresAt) {
    AccessRequestRecord record = new AccessRequestRecord();
    record.setUserId(String.valueOf(userId));
    record.setUsername("Some_User");
    record.setFirstName("Alex");
    record.setLanguageCode("ru");
    record.setRequestedAt(NOW.toEpochMilli());
    record.setExpiresAt(expiresAt);
    return record;
  }

}
