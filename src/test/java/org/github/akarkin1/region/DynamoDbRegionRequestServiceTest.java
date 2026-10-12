package org.github.akarkin1.region;

import org.github.akarkin1.dynamodb.FakeTable;
import org.github.akarkin1.dynamodb.RegionRequestRecord;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamoDbRegionRequestServiceTest {

  private static final Instant NOW = Instant.parse("2026-10-12T12:00:00.123Z");
  private static final long THIRTY_DAYS_SECONDS = Duration.ofDays(30).toSeconds();
  private static final long USER_ID = 123456789L;
  private static final String REGION = "ap-southeast-2";
  /** Requested "now", so the expected TTL is the same whether it is derived from the request or the clock. */
  private static final RegionRequest REQUEST =
      new RegionRequest(REGION, USER_ID, "Some_User", "Alex", "Smith", "ru", NOW.toEpochMilli());

  private final RecordingRequestMetrics metrics = new RecordingRequestMetrics();
  private final FakeTable<RegionRequestRecord> table =
      FakeTable.of(RegionRequestRecord.class, () -> metrics.isTiming(MetricComponent.DYNAMODB));

  private DynamoDbRegionRequestService service;

  @BeforeEach
  void setUp() {
    service = new DynamoDbRegionRequestService(table.table(), Clock.fixed(NOW, ZoneOffset.UTC),
                                               metrics);
  }

  @Test
  @DisplayName("5 AC-5, §5: create puts REGION_REQUEST/<region> with the request's fields and expiresAt = requestedAt + 30 days (epoch seconds)")
  void createPuts() {
    service.create(REQUEST);

    assertEquals(1, table.puts().size());
    RegionRequestRecord item = table.puts().getFirst().item();
    Map<String, AttributeValue> stored = table.schema().itemToMap(item, true);
    assertEquals(Map.of(
        "pk", AttributeValue.fromS("REGION_REQUEST"),
        "sk", AttributeValue.fromS(REGION),
        "userId", AttributeValue.fromN("123456789"),
        "username", AttributeValue.fromS("Some_User"),
        "firstName", AttributeValue.fromS("Alex"),
        "lastName", AttributeValue.fromS("Smith"),
        "languageCode", AttributeValue.fromS("ru"),
        "requestedAt", AttributeValue.fromN(String.valueOf(NOW.toEpochMilli())),
        "expiresAt", AttributeValue.fromN(String.valueOf(NOW.getEpochSecond() + THIRTY_DAYS_SECONDS))),
                 stored);
  }

  @Test
  @DisplayName("5 §4.6, D-5: the TTL is derived from requestedAt, not from the clock")
  void createExpiryFromRequestedAt() {
    Instant requestedAt = Instant.parse("2026-10-01T08:30:00.999Z");

    service.create(new RegionRequest(REGION, USER_ID, "Some_User", "Alex", "Smith", "en",
                                     requestedAt.toEpochMilli()));

    RegionRequestRecord item = table.puts().getFirst().item();
    assertEquals(requestedAt.toEpochMilli(), item.getRequestedAt());
    assertEquals(requestedAt.getEpochSecond() + THIRTY_DAYS_SECONDS, item.getExpiresAt());
  }

  @Test
  @DisplayName("5 §5: create of a user without a username or a last name writes neither attribute")
  void createWithoutUsername() {
    service.create(new RegionRequest(REGION, USER_ID, null, "Alex", null, "en",
                                     NOW.toEpochMilli()));

    Map<String, AttributeValue> stored =
        table.schema().itemToMap(table.puts().getFirst().item(), true);
    assertFalse(stored.containsKey("username"));
    assertFalse(stored.containsKey("lastName"));
  }

  @Test
  @DisplayName("5 AC-5: find reads REGION_REQUEST/<region> and maps it to a RegionRequest")
  void findMapsRecord() {
    table.withItem(record(REGION, USER_ID, NOW.getEpochSecond() + THIRTY_DAYS_SECONDS));

    Optional<RegionRequest> found = service.find(REGION);

    assertEquals(Optional.of(REQUEST), found);
    assertEquals(List.of(FakeTable.key("REGION_REQUEST", REGION)), table.gets());
  }

  @Test
  @DisplayName("5 AC-5: find of a region without a request is empty")
  void findMissing() {
    assertEquals(Optional.empty(), service.find("eu-west-3"));
    assertEquals(List.of(FakeTable.key("REGION_REQUEST", "eu-west-3")), table.gets());
  }

  @Test
  @DisplayName("5 AC-5, §4.6: find ignores an expired request that TTL has not removed yet")
  void findIgnoresExpired() {
    table.withItem(record(REGION, USER_ID, NOW.getEpochSecond() - 1));

    assertEquals(Optional.empty(), service.find(REGION));
  }

  @Test
  @DisplayName("5 AC-5: findAll queries pk = REGION_REQUEST once and maps the records of every page")
  void findAllAcrossPages() {
    long future = NOW.getEpochSecond() + THIRTY_DAYS_SECONDS;
    table.withPages(List.of(record(REGION, USER_ID, future), record("eu-west-3", 42L, future)),
                    List.of(record("sa-east-1", 7L, future)));

    List<RegionRequest> all = service.findAll();

    assertEquals(3, all.size(), all.toString());
    assertEquals(Set.of(REQUEST, request("eu-west-3", 42L), request("sa-east-1", 7L)),
                 Set.copyOf(all));
    assertEquals(1, table.queries().size());
    assertEquals(Map.of("pk", AttributeValue.fromS("REGION_REQUEST")),
                 table.keyCondition(table.queries().getFirst()));
  }

  @Test
  @DisplayName("5 AC-5, §4.6: findAll skips expired requests")
  void findAllSkipsExpired() {
    long future = NOW.getEpochSecond() + THIRTY_DAYS_SECONDS;
    table.withPages(List.of(record("eu-west-3", 42L, NOW.getEpochSecond() - 1),
                            record(REGION, USER_ID, future)),
                    List.of(record("sa-east-1", 7L, NOW.getEpochSecond() - 3600)));

    assertEquals(List.of(REQUEST), service.findAll());
  }

  @Test
  @DisplayName("5 AC-5: findAll of an empty table is empty")
  void findAllEmpty() {
    table.withPages(List.of());

    assertEquals(List.of(), service.findAll());
  }

  @Test
  @DisplayName("5 AC-5: delete deletes REGION_REQUEST/<region>")
  void deleteDeletes() {
    service.delete(REGION);

    assertEquals(List.of(FakeTable.key("REGION_REQUEST", REGION)), table.deletes());
    assertEquals(List.of(), table.puts());
  }

  @Test
  @DisplayName("5 AC-5: every DynamoDB call (and every query page) runs inside a DYNAMODB timing")
  void callsTimed() {
    long future = NOW.getEpochSecond() + THIRTY_DAYS_SECONDS;
    table.withItem(record(REGION, USER_ID, future))
        .withPages(List.of(record(REGION, USER_ID, future)), List.of(record("sa-east-1", 7L, future)));

    service.find(REGION);
    service.findAll();
    service.create(REQUEST);
    service.delete(REGION);

    // getItem, query + 2 pages, putItem, deleteItem
    assertEquals(6, table.timedCalls().size(), table.timedCalls().toString());
    assertTrue(table.timedCalls().stream().allMatch(Boolean::booleanValue),
               "calls outside a DYNAMODB timing: " + table.timedCalls());
    assertTrue(metrics.timed().stream().allMatch(MetricComponent.DYNAMODB::equals),
               metrics.timed().toString());
  }

  private static RegionRequest request(String regionId, long userId) {
    return new RegionRequest(regionId, userId, "Some_User", "Alex", "Smith", "ru",
                             NOW.toEpochMilli());
  }

  private static RegionRequestRecord record(String regionId, long userId, long expiresAt) {
    RegionRequestRecord record = new RegionRequestRecord();
    record.setRegionId(regionId);
    record.setUserId(userId);
    record.setUsername("Some_User");
    record.setFirstName("Alex");
    record.setLastName("Smith");
    record.setLanguageCode("ru");
    record.setRequestedAt(NOW.toEpochMilli());
    record.setExpiresAt(expiresAt);
    return record;
  }

}
