package org.github.akarkin1.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

/**
 * Turns a user pre-registered by username ({@code PENDING_USER} record) into a {@code USER} record
 * keyed by the user id at their first contact: writes {@code USER} with the pending permissions,
 * then deletes the pending record. Called once per request, before any permission check, so that
 * {@link Authorizer#hasPermission} stays a read-only query.
 *
 * <p>A failed write is logged and leaves the pending record in place: the user has no access in
 * this request and the activation is retried on their next one.
 *
 * @deprecated Pre-registration by username ended with 2026.10. Remove once
 *     {@code PENDING_USER} records no longer exist (query {@code pk = PENDING_USER}); see
 *     docs/specs/cleanup-2026-10.md §4.3.
 */
@Deprecated(forRemoval = true)
@Log4j2
@RequiredArgsConstructor
public class PendingUserActivator {

  private final PermissionsService permissionsService;

  /** Activates the pending record of {@code user}'s username, if there is one and no USER record. */
  public void activate(TgUser user) {
    if (user == null || user.id() == null || user.username() == null) {
      return;
    }

    Map<TgUser, List<Permission>> users = permissionsService.getUsers();
    if (users.containsKey(user)) {
      return;
    }
    // the stored key: TgUser equality ignores the username's case, the record key does not
    TgUser pendingUser = users.keySet().stream()
        .filter(new TgUser(null, user.username())::equals)
        .findFirst()
        .orElse(null);
    if (pendingUser == null) {
      return;
    }

    log.info("Activating pending user @{} as user {}", pendingUser.username(), user.id());
    try {
      permissionsService.updateUserPermissions(user, new HashSet<>(users.get(pendingUser)));
      permissionsService.deleteUser(pendingUser);
    } catch (RuntimeException e) {
      log.error("Failed to activate pending user @{} as user {}", user.username(), user.id(), e);
    }
  }

}
