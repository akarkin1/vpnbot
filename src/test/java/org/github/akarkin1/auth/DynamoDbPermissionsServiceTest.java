package org.github.akarkin1.auth;

import org.github.akarkin1.dynamodb.FakeTable;
import org.github.akarkin1.dynamodb.PendingUserRecord;
import org.github.akarkin1.dynamodb.UserRecord;
import org.github.akarkin1.metrics.MetricComponent;
import org.github.akarkin1.metrics.RecordingRequestMetrics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.enhanced.dynamodb.model.PutItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DynamoDbPermissionsServiceTest {

  private final RecordingRequestMetrics metrics = new RecordingRequestMetrics();
  private static final TgUser ALICE = new TgUser(1L, "alice");
  private static final TgUser BOB = new TgUser(2L, "bob");
  private static final TgUser CAROL = new TgUser(3L, "Carol_99");

  private final FakeTable<UserRecord> users =
      FakeTable.of(UserRecord.class, () -> metrics.isTiming(MetricComponent.DYNAMODB));
  private final FakeTable<PendingUserRecord> pendingUsers =
      FakeTable.of(PendingUserRecord.class, () -> metrics.isTiming(MetricComponent.DYNAMODB));

  private DynamoDbPermissionsService service;

  @BeforeEach
  void setUp() {
    service = new DynamoDbPermissionsService(users.table(), pendingUsers.table(), metrics);
  }

  @Test
  @DisplayName("3 AC-A3: permissions of all users are read with a query pk = USER across all pages")
  void allUsersAcrossPages() {
    users.withPages(List.of(user(ALICE, "RUN_NODES", "LIST_NODES"), user(BOB, "ROOT_ACCESS")),
                    List.of(user(CAROL, "USER_MANAGEMENT")));

    Map<TgUser, List<Permission>> result = service.getUsers();

    assertEquals(Set.of(ALICE, BOB, CAROL), result.keySet());
    assertEquals(Set.of(Permission.RUN_NODES, Permission.LIST_NODES), Set.copyOf(result.get(ALICE)));
    assertEquals(List.of(Permission.ROOT_ACCESS), result.get(BOB));
    assertEquals(List.of(Permission.USER_MANAGEMENT), result.get(CAROL));
    assertEquals(1, users.queries().size());
    assertEquals(Map.of("pk", AttributeValue.fromS("USER")),
                 users.keyCondition(users.queries().getFirst()));
  }

  @Test
  @DisplayName("3 AC-A3: unknown permission names are skipped")
  void unknownPermissionsSkipped() {
    users.withPages(List.of(user(ALICE, "RUN_NODES", "FLY_TO_THE_MOON")));

    Map<TgUser, List<Permission>> result = service.getUsers();

    assertEquals(List.of(Permission.RUN_NODES), result.get(ALICE));
  }

  @Test
  @DisplayName("3 AC-A3: no users means an empty map")
  void noUsers() {
    users.withPages(List.of());

    assertTrue(service.getUsers().isEmpty());
  }

  @Test
  @DisplayName("3 AC-A4: non-empty permissions are written with PutItem as a String Set of enum names")
  void updatePuts() {
    service.updateUserPermissions(ALICE, EnumSet.of(Permission.RUN_NODES, Permission.LIST_NODES));

    assertEquals(List.of(), users.deletes());
    assertEquals(1, users.puts().size());
    PutItemEnhancedRequest<UserRecord> request = users.puts().getFirst();
    assertNull(request.conditionExpression(), "the record is replaced unconditionally");
    UserRecord item = request.item();
    assertEquals("USER", item.getType());
    assertEquals("1", item.getUserId());
    assertEquals("alice", item.getUsername());
    assertEquals(Set.of("RUN_NODES", "LIST_NODES"), item.getPermissions());

    Map<String, AttributeValue> stored = users.schema().itemToMap(item, true);
    assertEquals(AttributeValue.fromS("USER"), stored.get("pk"));
    assertEquals(AttributeValue.fromS("1"), stored.get("sk"));
    assertEquals(AttributeValue.Type.SS, stored.get("permissions").type());
    assertEquals(Set.of("RUN_NODES", "LIST_NODES"), new HashSet<>(stored.get("permissions").ss()));
  }

  @Test
  @DisplayName("3 AC-A4: null permissions delete the user record")
  void nullDeletes() {
    service.updateUserPermissions(ALICE, null);

    assertEquals(List.of(FakeTable.key("USER", "1")), users.deletes());
    assertEquals(List.of(), users.puts());
  }

  @Test
  @DisplayName("3 AC-A4: empty permissions delete the user record")
  void emptyDeletes() {
    service.updateUserPermissions(ALICE, Set.of());

    assertEquals(List.of(FakeTable.key("USER", "1")), users.deletes());
    assertEquals(List.of(), users.puts());
  }

  @Test
  @DisplayName("3 AC-A4: assignRolesToUser ends in a PutItem with the roles' permissions")
  void assignRolesPuts() {
    service.assignRolesToUser(ALICE, Set.of(UserRole.READ_ONLY, UserRole.USER_ADMIN));

    assertEquals(1, users.puts().size());
    UserRecord item = users.puts().getFirst().item();
    assertEquals("alice", item.getUsername());
    assertEquals(names(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES,
                       Permission.USER_MANAGEMENT),
                 item.getPermissions());
  }

  @Test
  @DisplayName("3 AC-A4: deleteUser ends in a DeleteItem of the user")
  void deleteUserDeletes() {
    service.deleteUser(BOB);

    assertEquals(List.of(FakeTable.key("USER", "2")), users.deletes());
    assertEquals(List.of(), users.puts());
  }

  @Test
  @DisplayName("3 AC-A4, AC-A7: query, PutItem and DeleteItem run inside a DYNAMODB timing")
  void callsTimed() {
    users.withPages(List.of(user(ALICE, "RUN_NODES")), List.of(user(BOB, "ROOT_ACCESS")));

    service.getUsers();
    service.updateUserPermissions(ALICE, Set.of(Permission.LIST_NODES));
    service.updateUserPermissions(BOB, Set.of());

    assertEquals(1, users.queries().size());
    assertEquals(1, users.puts().size());
    assertEquals(1, users.deletes().size());
    assertFalse(users.timedCalls().contains(false),
                "DynamoDB call outside a timing: " + users.timedCalls());
    assertFalse(metrics.timed().isEmpty());
    assertTrue(metrics.timed().stream().allMatch(MetricComponent.DYNAMODB::equals),
               metrics.timed().toString());
  }

  private static UserRecord user(TgUser user, String... permissions) {
    UserRecord record = new UserRecord();
    record.setUserId(String.valueOf(user.id()));
    record.setUsername(user.username());
    record.setPermissions(new HashSet<>(List.of(permissions)));
    return record;
  }

  private static Set<String> names(Permission... permissions) {
    return Set.of(permissions).stream().map(Permission::name).collect(Collectors.toSet());
  }

}
