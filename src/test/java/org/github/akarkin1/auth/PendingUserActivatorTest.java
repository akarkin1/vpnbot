package org.github.akarkin1.auth;

import org.github.akarkin1.auth.FakePermissionsService.Write;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs against {@link FakePermissionsService}, so the activation is observed as the sequence of
 * record writes, whichever of {@code updateUserPermissions}/{@code deleteUser} the code uses.
 */
// Pending users are deprecated: pre-registration by username ended with 2026.10. Remove once
// PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
@SuppressWarnings("removal")
class PendingUserActivatorTest {

  private static final TgUser ALICE = new TgUser(42L, "alice");

  private final FakePermissionsService permissions = new FakePermissionsService();
  private final PendingUserActivator activator = new PendingUserActivator(permissions);

  @Test
  @DisplayName("4 AC-2a, D-2: no USER record but a PENDING_USER for the username: USER is written, then PENDING_USER deleted")
  void pendingUserIsActivated() {
    permissions.withPending("alice", Permission.LIST_NODES, Permission.RUN_NODES);

    activator.activate(ALICE);

    assertEquals(List.of(Write.putUser(42L, "alice", Permission.LIST_NODES, Permission.RUN_NODES),
                         Write.deletePending("alice")),
                 permissions.writes());
    assertEquals(Optional.of(ALICE), permissions.storedUser(42L));
    assertEquals(Set.of(Permission.LIST_NODES, Permission.RUN_NODES), permissions.permissionsOf(42L));
    assertFalse(permissions.hasPending("alice"));
  }

  @Test
  @DisplayName("4 AC-2a: a second request after the activation writes nothing")
  void activatedOnce() {
    permissions.withPending("alice", Permission.LIST_NODES);

    activator.activate(ALICE);
    activator.activate(ALICE);

    assertEquals(2, permissions.writes().size(), "activated once: " + permissions.writes());
  }

  @Test
  @DisplayName("4 AC-2a, D-16: the PENDING_USER lookup is case-insensitive (@Alice pre-registered, alice writes)")
  void pendingLookupIgnoresCase() {
    permissions.withPending("Alice", Permission.RUN_NODES);

    activator.activate(ALICE);

    assertEquals(Set.of(Permission.RUN_NODES), permissions.permissionsOf(42L));
    assertFalse(permissions.hasPending("Alice"));
  }

  @Test
  @DisplayName("4 AC-2a: a pending root is activated with ROOT_ACCESS")
  void pendingRootIsActivated() {
    permissions.withPending("alice", Permission.ROOT_ACCESS);

    activator.activate(ALICE);

    assertEquals(Set.of(Permission.ROOT_ACCESS), permissions.permissionsOf(42L));
  }

  @Test
  @DisplayName("4 AC-2a: a user with a USER record is left alone, even with a PENDING_USER of the same username")
  void userRecordIsNotTouched() {
    permissions.withUser(42L, "alice", Permission.LIST_NODES).withPending("alice", Permission.RUN_NODES);

    activator.activate(ALICE);

    assertEquals(List.of(), permissions.writes());
    assertEquals(Set.of(Permission.LIST_NODES), permissions.permissionsOf(42L));
    assertTrue(permissions.hasPending("alice"));
  }

  @Test
  @DisplayName("4 AC-2a: neither USER nor PENDING_USER: nothing is written")
  void unknownUser() {
    permissions.withUser(43L, "bob", Permission.RUN_NODES).withPending("carol", Permission.RUN_NODES);

    activator.activate(ALICE);

    assertEquals(List.of(), permissions.writes());
  }

  @Test
  @DisplayName("4 AC-2a: a request without a username, without an id, or without a user: no activation attempt")
  void nothingToMatchOn() {
    permissions.withPending("alice", Permission.RUN_NODES);

    activator.activate(new TgUser(42L, null));
    activator.activate(new TgUser(null, "alice"));
    activator.activate(null);

    assertEquals(List.of(), permissions.writes());
    assertTrue(permissions.hasPending("alice"));
  }

  @Test
  @DisplayName("4 AC-2a, D-15: a failed write is logged, the pending record stays and the request goes on")
  void failedWriteKeepsPendingRecord() {
    FakePermissionsService failing = new FakePermissionsService() {
      @Override
      public void updateUserPermissions(TgUser user, Set<Permission> permissions) {
        throw new IllegalStateException("DynamoDB is down");
      }
    };
    failing.withPending("alice", Permission.RUN_NODES);

    assertDoesNotThrow(() -> new PendingUserActivator(failing).activate(ALICE));

    assertTrue(failing.hasPending("alice"));
    assertTrue(failing.storedUser(42L).isEmpty());
  }

}
