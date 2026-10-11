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
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Records are built from and checked as DynamoDB attribute maps (pk, sk, username, permissions)
 * through the beans' real schemas, so the test does not depend on the beans' property names.
 */
// Pending users are deprecated: pre-registration by username ended with 2026.10. Remove once
// PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
@SuppressWarnings("removal")
class DynamoDbPermissionsServiceTest {

  private final RecordingRequestMetrics metrics = new RecordingRequestMetrics();
  private final FakeTable<UserRecord> users =
      FakeTable.of(UserRecord.class, () -> metrics.isTiming(MetricComponent.DYNAMODB));
  private final FakeTable<PendingUserRecord> pendingUsers =
      FakeTable.of(PendingUserRecord.class, () -> metrics.isTiming(MetricComponent.DYNAMODB));

  private DynamoDbPermissionsService service;

  @BeforeEach
  void setUp() {
    service = new DynamoDbPermissionsService(users.table(), pendingUsers.table(), metrics);
  }

  // --- getUsers -----------------------------------------------------------------------------

  @Test
  @DisplayName("4 AC-3: getUsers returns USER records (id + username, all pages) and PENDING_USER records (pending, no id)")
  void getUsersMergesUsersAndPending() {
    users.withPages(List.of(user("42", "alice", "RUN_NODES", "LIST_NODES"),
                            user("43", null, "LIST_NODES")),
                    List.of(user("1", "root", "ROOT_ACCESS")));
    pendingUsers.withPages(List.of(pending("Bob_99", "USER_MANAGEMENT")));

    Map<TgUser, List<Permission>> result = service.getUsers();

    assertEquals(Set.of(new TgUser(42L, "alice"), new TgUser(43L, null), new TgUser(1L, "root"),
                        new TgUser(null, "Bob_99")),
                 result.keySet());
    assertEquals(Set.of(Permission.RUN_NODES, Permission.LIST_NODES),
                 Set.copyOf(result.get(new TgUser(42L, "alice"))));
    assertEquals(List.of(Permission.LIST_NODES), result.get(new TgUser(43L, null)));
    assertEquals(List.of(Permission.ROOT_ACCESS), result.get(new TgUser(1L, "root")));
    assertEquals(List.of(Permission.USER_MANAGEMENT), result.get(new TgUser(null, "Bob_99")));
    assertTrue(new TgUser(null, "Bob_99").pending());
  }

  @Test
  @DisplayName("4 AC-3: getUsers queries pk = USER and pk = PENDING_USER")
  void getUsersQueriesBothPartitions() {
    users.withPages(List.of());
    pendingUsers.withPages(List.of());

    service.getUsers();

    assertEquals(1, users.queries().size());
    assertEquals(Map.of("pk", AttributeValue.fromS("USER")),
                 users.keyCondition(users.queries().getFirst()));
    assertEquals(1, pendingUsers.queries().size());
    assertEquals(Map.of("pk", AttributeValue.fromS("PENDING_USER")),
                 pendingUsers.keyCondition(pendingUsers.queries().getFirst()));
  }

  @Test
  @DisplayName("4 AC-3: unknown permission names are skipped in both record types")
  void unknownPermissionsSkipped() {
    users.withPages(List.of(user("42", "alice", "RUN_NODES", "FLY_TO_THE_MOON")));
    pendingUsers.withPages(List.of(pending("bob", "LIST_NODES", "TIME_TRAVEL")));

    Map<TgUser, List<Permission>> result = service.getUsers();

    assertEquals(List.of(Permission.RUN_NODES), result.get(new TgUser(42L, "alice")));
    assertEquals(List.of(Permission.LIST_NODES), result.get(new TgUser(null, "bob")));
  }

  @Test
  @DisplayName("4 AC-3: no records means an empty map")
  void noUsers() {
    users.withPages(List.of());
    pendingUsers.withPages(List.of());

    assertTrue(service.getUsers().isEmpty());
  }

  // --- updateUserPermissions ----------------------------------------------------------------

  @Test
  @DisplayName("4 AC-3: a user with an id is written as USER/<id> with the username attribute and the permissions as a String Set")
  void updateWithIdPutsUser() {
    service.updateUserPermissions(new TgUser(42L, "alice"),
                                  EnumSet.of(Permission.RUN_NODES, Permission.LIST_NODES));

    assertEquals(1, users.puts().size());
    PutItemEnhancedRequest<UserRecord> request = users.puts().getFirst();
    assertNull(request.conditionExpression(), "the record is replaced unconditionally");
    Map<String, AttributeValue> stored = users.schema().itemToMap(request.item(), true);
    assertEquals(Set.of("pk", "sk", "username", "permissions"), stored.keySet());
    assertEquals(AttributeValue.fromS("USER"), stored.get("pk"));
    assertEquals(AttributeValue.fromS("42"), stored.get("sk"));
    assertEquals(AttributeValue.fromS("alice"), stored.get("username"));
    assertEquals(AttributeValue.Type.SS, stored.get("permissions").type());
    assertEquals(Set.of("RUN_NODES", "LIST_NODES"), new HashSet<>(stored.get("permissions").ss()));
    assertEquals(List.of(), users.deletes());
    assertEquals(List.of(), pendingUsers.puts());
    assertEquals(List.of(), pendingUsers.deletes());
  }

  @Test
  @DisplayName("4 AC-3: a user with an id but no username keeps the stored username (read before the write)")
  void updateWithIdKeepsStoredUsername() {
    users.withItem(user("42", "alice", "LIST_NODES"));

    service.updateUserPermissions(new TgUser(42L, null), Set.of(Permission.RUN_NODES));

    assertEquals(List.of(FakeTable.key("USER", "42")), users.gets());
    assertEquals(1, users.puts().size());
    Map<String, AttributeValue> stored = users.schema().itemToMap(users.puts().getFirst().item(), true);
    assertEquals(AttributeValue.fromS("42"), stored.get("sk"));
    assertEquals(AttributeValue.fromS("alice"), stored.get("username"));
    assertEquals(List.of("RUN_NODES"), stored.get("permissions").ss());
  }

  @Test
  @DisplayName("4 AC-3: a user with an id, no username and no stored record is written without a username")
  void updateWithIdWithoutAnyUsername() {
    service.updateUserPermissions(new TgUser(42L, null), Set.of(Permission.RUN_NODES));

    assertEquals(1, users.puts().size());
    Map<String, AttributeValue> stored = users.schema().itemToMap(users.puts().getFirst().item(), true);
    assertEquals(Set.of("pk", "sk", "permissions"), stored.keySet());
    assertEquals(AttributeValue.fromS("42"), stored.get("sk"));
  }

  @Test
  @DisplayName("4 AC-3: a given username replaces the stored one")
  void updateWithIdReplacesUsername() {
    users.withItem(user("42", "alice_old", "LIST_NODES"));

    service.updateUserPermissions(new TgUser(42L, "alice_new"), Set.of(Permission.LIST_NODES));

    Map<String, AttributeValue> stored = users.schema().itemToMap(users.puts().getFirst().item(), true);
    assertEquals(AttributeValue.fromS("alice_new"), stored.get("username"));
  }

  @Test
  @DisplayName("4 AC-3: a user without an id is written as PENDING_USER/<username> with the permissions as a String Set")
  void updateWithoutIdPutsPending() {
    service.updateUserPermissions(new TgUser(null, "bob"),
                                  EnumSet.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES));

    assertEquals(1, pendingUsers.puts().size());
    PutItemEnhancedRequest<PendingUserRecord> request = pendingUsers.puts().getFirst();
    assertNull(request.conditionExpression(), "the record is replaced unconditionally");
    Map<String, AttributeValue> stored = pendingUsers.schema().itemToMap(request.item(), true);
    assertEquals(Set.of("pk", "sk", "permissions"), stored.keySet());
    assertEquals(AttributeValue.fromS("PENDING_USER"), stored.get("pk"));
    assertEquals(AttributeValue.fromS("bob"), stored.get("sk"));
    assertEquals(AttributeValue.Type.SS, stored.get("permissions").type());
    assertEquals(Set.of("SUPPORTED_REGIONS", "LIST_NODES"),
                 new HashSet<>(stored.get("permissions").ss()));
    assertEquals(List.of(), users.puts());
    assertEquals(List.of(), users.deletes());
  }

  @Test
  @DisplayName("4 AC-3: null permissions delete USER/<id>")
  void nullDeletesUser() {
    service.updateUserPermissions(new TgUser(42L, "alice"), null);

    assertEquals(List.of(FakeTable.key("USER", "42")), users.deletes());
    assertEquals(List.of(), users.puts());
    assertEquals(List.of(), pendingUsers.deletes());
  }

  @Test
  @DisplayName("4 AC-3: empty permissions delete USER/<id>")
  void emptyDeletesUser() {
    service.updateUserPermissions(new TgUser(42L, null), Set.of());

    assertEquals(List.of(FakeTable.key("USER", "42")), users.deletes());
    assertEquals(List.of(), users.puts());
  }

  @Test
  @DisplayName("4 AC-3: empty or null permissions of a user without an id delete PENDING_USER/<username>")
  void emptyDeletesPending() {
    service.updateUserPermissions(new TgUser(null, "bob"), Set.of());
    service.updateUserPermissions(new TgUser(null, "carol"), null);

    assertEquals(List.of(FakeTable.key("PENDING_USER", "bob"), FakeTable.key("PENDING_USER", "carol")),
                 pendingUsers.deletes());
    assertEquals(List.of(), pendingUsers.puts());
    assertEquals(List.of(), users.deletes());
  }

  @Test
  @DisplayName("4 AC-3: assignRolesToUser ends in a PutItem of USER/<id> with the roles' permissions")
  void assignRolesPuts() {
    service.assignRolesToUser(new TgUser(42L, "alice"), Set.of(UserRole.READ_ONLY, UserRole.USER_ADMIN));

    assertEquals(1, users.puts().size());
    Map<String, AttributeValue> stored = users.schema().itemToMap(users.puts().getFirst().item(), true);
    assertEquals(AttributeValue.fromS("42"), stored.get("sk"));
    assertEquals(Set.of("SUPPORTED_REGIONS", "LIST_NODES", "USER_MANAGEMENT"),
                 new HashSet<>(stored.get("permissions").ss()));
  }

  @Test
  @DisplayName("4 AC-3: deleteUser of a pending user ends in a DeleteItem of PENDING_USER/<username>")
  void deletePendingUser() {
    service.deleteUser(new TgUser(null, "bob"));

    assertEquals(List.of(FakeTable.key("PENDING_USER", "bob")), pendingUsers.deletes());
    assertEquals(List.of(), users.deletes());
  }

  @Test
  @DisplayName("4 AC-3: deleteUser of a user with an id ends in a DeleteItem of USER/<id>")
  void deleteUserWithId() {
    service.deleteUser(new TgUser(42L, "alice"));

    assertEquals(List.of(FakeTable.key("USER", "42")), users.deletes());
    assertEquals(List.of(), pendingUsers.deletes());
  }

  // --- metrics ------------------------------------------------------------------------------

  @Test
  @DisplayName("4 AC-3, 3 AC-A7: every call on both record types runs inside a DYNAMODB timing")
  void callsTimed() {
    users.withPages(List.of(user("42", "alice", "RUN_NODES")), List.of(user("1", "root", "ROOT_ACCESS")))
        .withItem(user("42", "alice", "RUN_NODES"));
    pendingUsers.withPages(List.of(pending("bob", "LIST_NODES")));

    service.getUsers();
    service.updateUserPermissions(new TgUser(42L, null), Set.of(Permission.LIST_NODES));
    service.updateUserPermissions(new TgUser(1L, "root"), Set.of());
    service.updateUserPermissions(new TgUser(null, "bob"), Set.of(Permission.RUN_NODES));
    service.updateUserPermissions(new TgUser(null, "carol"), null);

    assertEquals(1, users.puts().size());
    assertEquals(1, users.deletes().size());
    assertEquals(1, pendingUsers.puts().size());
    assertEquals(1, pendingUsers.deletes().size());
    assertFalse(users.timedCalls().contains(false),
                "USER call outside a timing: " + users.timedCalls());
    assertFalse(pendingUsers.timedCalls().contains(false),
                "PENDING_USER call outside a timing: " + pendingUsers.timedCalls());
    assertFalse(metrics.timed().isEmpty());
    assertTrue(metrics.timed().stream().allMatch(MetricComponent.DYNAMODB::equals),
               metrics.timed().toString());
  }

  private UserRecord user(String id, String username, String... permissions) {
    Map<String, AttributeValue> item = new HashMap<>();
    item.put("pk", AttributeValue.fromS("USER"));
    item.put("sk", AttributeValue.fromS(id));
    if (username != null) {
      item.put("username", AttributeValue.fromS(username));
    }
    item.put("permissions", AttributeValue.fromSs(List.of(permissions)));
    return users.schema().mapToItem(item);
  }

  private PendingUserRecord pending(String username, String... permissions) {
    return pendingUsers.schema().mapToItem(Map.of(
        "pk", AttributeValue.fromS("PENDING_USER"),
        "sk", AttributeValue.fromS(username),
        "permissions", AttributeValue.fromSs(List.of(permissions))));
  }

}
