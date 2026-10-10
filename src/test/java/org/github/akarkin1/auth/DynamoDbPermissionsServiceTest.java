package org.github.akarkin1.auth;

import org.github.akarkin1.dynamodb.FakeTable;
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
  private final FakeTable<UserRecord> users =
      FakeTable.of(UserRecord.class, () -> metrics.isTiming(MetricComponent.DYNAMODB));

  private DynamoDbPermissionsService service;

  @BeforeEach
  void setUp() {
    service = new DynamoDbPermissionsService(users.table(), metrics);
  }

  @Test
  @DisplayName("3 AC-A3: permissions of all users are read with a query pk = USER across all pages")
  void allUsersAcrossPages() {
    users.withPages(List.of(user("alice", "RUN_NODES", "LIST_NODES"), user("bob", "ROOT_ACCESS")),
                    List.of(user("Carol_99", "USER_MANAGEMENT")));

    Map<String, List<Permission>> result = service.getUserPermissions();

    assertEquals(Set.of("alice", "bob", "Carol_99"), result.keySet());
    assertEquals(Set.of(Permission.RUN_NODES, Permission.LIST_NODES), Set.copyOf(result.get("alice")));
    assertEquals(List.of(Permission.ROOT_ACCESS), result.get("bob"));
    assertEquals(List.of(Permission.USER_MANAGEMENT), result.get("Carol_99"));
    assertEquals(1, users.queries().size());
    assertEquals(Map.of("pk", AttributeValue.fromS("USER")),
                 users.keyCondition(users.queries().getFirst()));
  }

  @Test
  @DisplayName("3 AC-A3: unknown permission names are skipped")
  void unknownPermissionsSkipped() {
    users.withPages(List.of(user("alice", "RUN_NODES", "FLY_TO_THE_MOON")));

    Map<String, List<Permission>> result = service.getUserPermissions();

    assertEquals(List.of(Permission.RUN_NODES), result.get("alice"));
  }

  @Test
  @DisplayName("3 AC-A3: no users means an empty map")
  void noUsers() {
    users.withPages(List.of());

    assertTrue(service.getUserPermissions().isEmpty());
  }

  @Test
  @DisplayName("3 AC-A4: non-empty permissions are written with PutItem as a String Set of enum names")
  void updatePuts() {
    service.updateUserPermissions("alice", EnumSet.of(Permission.RUN_NODES, Permission.LIST_NODES));

    assertEquals(List.of(), users.deletes());
    assertEquals(1, users.puts().size());
    PutItemEnhancedRequest<UserRecord> request = users.puts().getFirst();
    assertNull(request.conditionExpression(), "the record is replaced unconditionally");
    UserRecord item = request.item();
    assertEquals("USER", item.getType());
    assertEquals("alice", item.getUsername());
    assertEquals(Set.of("RUN_NODES", "LIST_NODES"), item.getPermissions());

    Map<String, AttributeValue> stored = users.schema().itemToMap(item, true);
    assertEquals(AttributeValue.fromS("USER"), stored.get("pk"));
    assertEquals(AttributeValue.fromS("alice"), stored.get("sk"));
    assertEquals(AttributeValue.Type.SS, stored.get("permissions").type());
    assertEquals(Set.of("RUN_NODES", "LIST_NODES"), new HashSet<>(stored.get("permissions").ss()));
  }

  @Test
  @DisplayName("3 AC-A4: null permissions delete the user record")
  void nullDeletes() {
    service.updateUserPermissions("alice", null);

    assertEquals(List.of(FakeTable.key("USER", "alice")), users.deletes());
    assertEquals(List.of(), users.puts());
  }

  @Test
  @DisplayName("3 AC-A4: empty permissions delete the user record")
  void emptyDeletes() {
    service.updateUserPermissions("alice", Set.of());

    assertEquals(List.of(FakeTable.key("USER", "alice")), users.deletes());
    assertEquals(List.of(), users.puts());
  }

  @Test
  @DisplayName("3 AC-A4: assignRolesToUser ends in a PutItem with the roles' permissions")
  void assignRolesPuts() {
    service.assignRolesToUser("alice", Set.of(UserRole.READ_ONLY, UserRole.USER_ADMIN));

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
    service.deleteUser("bob");

    assertEquals(List.of(FakeTable.key("USER", "bob")), users.deletes());
    assertEquals(List.of(), users.puts());
  }

  @Test
  @DisplayName("3 AC-A4, AC-A7: query, PutItem and DeleteItem run inside a DYNAMODB timing")
  void callsTimed() {
    users.withPages(List.of(user("alice", "RUN_NODES")), List.of(user("bob", "ROOT_ACCESS")));

    service.getUserPermissions();
    service.updateUserPermissions("alice", Set.of(Permission.LIST_NODES));
    service.updateUserPermissions("bob", Set.of());

    assertEquals(1, users.queries().size());
    assertEquals(1, users.puts().size());
    assertEquals(1, users.deletes().size());
    assertFalse(users.timedCalls().contains(false),
                "DynamoDB call outside a timing: " + users.timedCalls());
    assertFalse(metrics.timed().isEmpty());
    assertTrue(metrics.timed().stream().allMatch(MetricComponent.DYNAMODB::equals),
               metrics.timed().toString());
  }

  private static UserRecord user(String username, String... permissions) {
    UserRecord record = new UserRecord();
    record.setUsername(username);
    record.setPermissions(new HashSet<>(List.of(permissions)));
    return record;
  }

  private static Set<String> names(Permission... permissions) {
    return Set.of(permissions).stream().map(Permission::name).collect(Collectors.toSet());
  }

}
