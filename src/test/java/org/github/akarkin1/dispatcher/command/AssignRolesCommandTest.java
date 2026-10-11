package org.github.akarkin1.dispatcher.command;

import org.github.akarkin1.auth.FakePermissionsService;
import org.github.akarkin1.auth.FakePermissionsService.Write;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.UserRole;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** {@code /assignRoles} keeps its syntax (§4.6); a username is resolved with findByUsername. */
class AssignRolesCommandTest {

  private static final String SUCCESS = "${command.assign-roles.roles-assigned.success.message}";

  private final FakePermissionsService permissions = new FakePermissionsService()
      .withUser(42L, "alice", Permission.LIST_NODES)
      .withPending("bob", Permission.LIST_NODES);
  private final AssignRolesCommand command = new AssignRolesCommand(permissions);

  @Test
  @DisplayName("4 AC-10: /assignRoles @unknown ROLE creates a PENDING_USER record (pre-registration)")
  void unknownUsernameCreatesPendingUser() {
    TextCommandResponse response = command.run(List.of("@unknown", "NODE_ADMIN"));

    assertEquals(List.of(Write.putPending("unknown", Permission.SUPPORTED_REGIONS,
                                          Permission.LIST_NODES, Permission.RUN_NODES)),
                 permissions.writes());
    assertEquals(SUCCESS, response.text());
    assertEquals("unknown", response.params()[0]);
    assertEquals(Set.of(UserRole.NODE_ADMIN), response.params()[1]);
  }

  @Test
  @DisplayName("4 AC-10, §5: the pending record keeps the username as typed, without the @")
  void pendingUsernameAsTyped() {
    command.run(List.of("@New_User", "READ_ONLY"));

    assertEquals(List.of(Write.putPending("New_User", Permission.SUPPORTED_REGIONS,
                                          Permission.LIST_NODES)),
                 permissions.writes());
  }

  @Test
  @DisplayName("4 AC-10: a known user is updated under the user id")
  void knownUserUpdatedUnderId() {
    command.run(List.of("@alice", "READ_ONLY", "USER_ADMIN"));

    assertEquals(List.of(Write.putUser(42L, "alice", Permission.SUPPORTED_REGIONS,
                                       Permission.LIST_NODES, Permission.USER_MANAGEMENT)),
                 permissions.writes());
    assertFalse(permissions.hasPending("alice"));
  }

  @Test
  @DisplayName("4 AC-10: the username is resolved case-insensitively and without @")
  void knownUserResolvedIgnoringCase() {
    command.run(List.of("ALICE", "NODE_ADMIN"));

    assertEquals(1, permissions.writes().size());
    assertEquals("USER", permissions.writes().getFirst().type());
    assertEquals("42", permissions.writes().getFirst().key());
    assertEquals(Set.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES, Permission.RUN_NODES),
                 permissions.permissionsOf(42L));
    assertEquals("alice", permissions.storedUser(42L).orElseThrow().username());
  }

  @Test
  @DisplayName("4 AC-10: an already pending user gets its PENDING_USER record replaced")
  void pendingUserUpdated() {
    command.run(List.of("@bob", "USER_ADMIN"));

    assertEquals(List.of(Write.putPending("bob", Permission.USER_MANAGEMENT)), permissions.writes());
  }

  @Test
  @DisplayName("4 AC-10, §4.6: a numeric id is accepted; the stored username is kept")
  void numericIdOfKnownUser() {
    command.run(List.of("42", "READ_ONLY"));

    assertEquals(List.of(Write.putUser(42L, "alice", Permission.SUPPORTED_REGIONS,
                                       Permission.LIST_NODES)),
                 permissions.writes());
  }

  @Test
  @DisplayName("4 AC-10, §4.6: a numeric id of a user not known yet writes a USER record for that id")
  void numericIdOfNewUser() {
    command.run(List.of("123456789", "READ_ONLY"));

    assertEquals(1, permissions.writes().size());
    Write write = permissions.writes().getFirst();
    assertEquals("USER", write.type());
    assertEquals("123456789", write.key());
    assertEquals(Set.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES), write.permissions());
    assertFalse(permissions.hasPending("123456789"));
  }

  @Test
  @DisplayName("§4.6 (unchanged): no arguments → invalid syntax, nothing is written")
  void noArguments() {
    TextCommandResponse response = command.run(List.of());

    assertTrue(response.text().contains("${command.assign-roles.invalid-syntax.no-username.error}"),
               response.text());
    assertEquals(List.of(), permissions.writes());
  }

  @Test
  @DisplayName("§4.6 (unchanged): a username without roles → invalid syntax, nothing is written")
  void noRoles() {
    TextCommandResponse response = command.run(List.of("@unknown"));

    assertTrue(response.text().contains("${command.assign-roles.invalid-syntax.no-role.error}"),
               response.text());
    assertEquals(List.of(), permissions.writes());
  }

  @Test
  @DisplayName("§4.6 (unchanged): an unknown role → error, nothing is written")
  void unknownRole() {
    TextCommandResponse response = command.run(List.of("@unknown", "SUPERHERO"));

    assertEquals("${command.assign-roles.no-role-exists.error}", response.text());
    assertEquals(List.of(), permissions.writes());
    assertEquals(Optional.empty(), permissions.findByUsername("unknown"));
  }

  @Test
  @DisplayName("§4.6 (unchanged): /assignRoles requires USER_MANAGEMENT")
  void requiredPermission() {
    assertEquals(List.of(Permission.USER_MANAGEMENT), command.getRequiredPermissions());
  }

}
