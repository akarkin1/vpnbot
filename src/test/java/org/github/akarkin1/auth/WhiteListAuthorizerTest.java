package org.github.akarkin1.auth;

import org.github.akarkin1.auth.FakePermissionsService.Write;
import org.github.akarkin1.config.YamlApplicationConfiguration.AuthConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against {@link FakePermissionsService}, so the activation is observed as the sequence of
 * record writes, whichever of {@code updateUserPermissions}/{@code deleteUser} the code uses.
 */
class WhiteListAuthorizerTest {

  private static final TgUser ALICE = new TgUser(42L, "alice");

  private final FakePermissionsService permissions = new FakePermissionsService();

  @Test
  @DisplayName("4 AC-2: a USER record for the id answers the check; nothing is written")
  void userRecordAnswers() {
    permissions.withUser(42L, "alice", Permission.LIST_NODES, Permission.RUN_NODES);
    Authorizer authorizer = authorizer(true);

    assertAll(
        () -> assertTrue(authorizer.hasPermission(ALICE, Permission.RUN_NODES)),
        () -> assertTrue(authorizer.hasPermission(ALICE, Permission.LIST_NODES)),
        () -> assertFalse(authorizer.hasPermission(ALICE, Permission.USER_MANAGEMENT)));
    assertEquals(List.of(), permissions.writes());
  }

  @Test
  @DisplayName("4 AC-2 (goal 1): the USER record is found by the id, so a renamed account keeps its access")
  void renamedUserKeepsAccess() {
    permissions.withUser(42L, "alice_old", Permission.RUN_NODES);

    assertTrue(authorizer(true).hasPermission(new TgUser(42L, "alice_new"), Permission.RUN_NODES));
  }

  @Test
  @DisplayName("4 AC-2 (goal 1): a username freed by another account gives no access to its new holder")
  void freedUsernameIsNotTakenOver() {
    permissions.withUser(43L, "alice", Permission.ROOT_ACCESS);

    assertFalse(authorizer(true).hasPermission(ALICE, Permission.RUN_NODES));
    assertEquals(List.of(), permissions.writes());
  }

  @Test
  @DisplayName("4 AC-2, D-2: no USER record but a PENDING_USER for the username: USER is written, then PENDING_USER deleted, and the check is answered")
  void pendingUserIsActivated() {
    permissions.withPending("alice", Permission.LIST_NODES, Permission.RUN_NODES);
    Authorizer authorizer = authorizer(true);

    assertTrue(authorizer.hasPermission(ALICE, Permission.RUN_NODES));

    assertEquals(List.of(Write.putUser(42L, "alice", Permission.LIST_NODES, Permission.RUN_NODES),
                         Write.deletePending("alice")),
                 permissions.writes());
    assertEquals(Optional.of(ALICE), permissions.storedUser(42L));
    assertEquals(Set.of(Permission.LIST_NODES, Permission.RUN_NODES), permissions.permissionsOf(42L));
    assertFalse(permissions.hasPending("alice"));
  }

  @Test
  @DisplayName("4 AC-2, D-2: an activated user is answered from the new record (a permission the pending record lacks is denied)")
  void activatedUserAnsweredFromNewRecord() {
    permissions.withPending("alice", Permission.LIST_NODES);
    Authorizer authorizer = authorizer(true);

    assertFalse(authorizer.hasPermission(ALICE, Permission.RUN_NODES));
    assertTrue(authorizer.hasPermission(ALICE, Permission.LIST_NODES));

    assertEquals(Set.of(Permission.LIST_NODES), permissions.permissionsOf(42L));
    assertFalse(permissions.hasPending("alice"));
    assertEquals(2, permissions.writes().size(), "activated once: " + permissions.writes());
  }

  @Test
  @DisplayName("4 AC-2, D-2: the PENDING_USER lookup is case-insensitive like findByUsername (@Alice pre-registered, alice writes)")
  void pendingLookupIgnoresCase() {
    permissions.withPending("Alice", Permission.RUN_NODES);

    assertTrue(authorizer(true).hasPermission(ALICE, Permission.RUN_NODES));

    assertEquals(Set.of(Permission.RUN_NODES), permissions.permissionsOf(42L));
    assertFalse(permissions.hasPending("Alice"));
  }

  @Test
  @DisplayName("4 AC-2: a pending root is activated and then has every permission")
  void pendingRootIsActivated() {
    permissions.withPending("alice", Permission.ROOT_ACCESS);
    Authorizer authorizer = authorizer(true);

    assertTrue(authorizer.hasPermission(ALICE, Permission.DELETE_USERS));
    assertTrue(authorizer.hasPermission(ALICE, Permission.RUN_NODES));
    assertEquals(Set.of(Permission.ROOT_ACCESS), permissions.permissionsOf(42L));
  }

  @Test
  @DisplayName("4 AC-2: neither USER nor PENDING_USER: false and nothing is written")
  void unknownUser() {
    permissions.withUser(43L, "bob", Permission.RUN_NODES).withPending("carol", Permission.RUN_NODES);

    assertFalse(authorizer(true).hasPermission(ALICE, Permission.RUN_NODES));
    assertEquals(List.of(), permissions.writes());
  }

  @Test
  @DisplayName("4 AC-2: a request without a username and without a USER record: false, no activation attempt")
  void noUsernameNoUserRecord() {
    permissions.withPending("alice", Permission.RUN_NODES);

    assertFalse(authorizer(true).hasPermission(new TgUser(42L, null), Permission.RUN_NODES));
    assertEquals(List.of(), permissions.writes());
    assertTrue(permissions.hasPending("alice"));
  }

  @Test
  @DisplayName("4 AC-2: a request without a username is answered from the USER record of its id")
  void noUsernameWithUserRecord() {
    permissions.withUser(42L, null, Permission.LIST_NODES);

    assertTrue(authorizer(true).hasPermission(new TgUser(42L, null), Permission.LIST_NODES));
    assertFalse(authorizer(true).hasPermission(new TgUser(42L, null), Permission.RUN_NODES));
  }

  @Test
  @DisplayName("4 AC-2: auth disabled: every check is true and nothing is read or written")
  void authDisabled() {
    Authorizer authorizer = authorizer(false);

    assertTrue(authorizer.hasPermission(ALICE, Permission.ROOT_ACCESS));
    assertTrue(authorizer.hasPermission(new TgUser(42L, null), Permission.DELETE_USERS));
    assertEquals(List.of(), permissions.writes());
  }

  @Test
  @DisplayName("4 AC-2: ROOT_ACCESS implies every permission")
  void rootImpliesAll() {
    permissions.withUser(1L, "root", Permission.ROOT_ACCESS);
    Authorizer authorizer = authorizer(true);
    TgUser root = new TgUser(1L, "root");

    for (Permission permission : Permission.values()) {
      assertTrue(authorizer.hasPermission(root, permission), permission.name());
    }
  }

  @Test
  @DisplayName("4 AC-2: a USER record wins over a PENDING_USER of the same username (no activation)")
  void userRecordWinsOverPending() {
    permissions.withUser(42L, "alice", Permission.LIST_NODES).withPending("alice", Permission.RUN_NODES);

    assertFalse(authorizer(true).hasPermission(ALICE, Permission.RUN_NODES));
    assertEquals(List.of(), permissions.writes());
  }

  private WhiteListAuthorizer authorizer(boolean enabled) {
    AuthConfiguration config = new AuthConfiguration();
    config.setEnabled(enabled);
    return new WhiteListAuthorizer(config, permissions);
  }

}
