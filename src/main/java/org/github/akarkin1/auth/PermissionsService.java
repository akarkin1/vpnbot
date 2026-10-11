package org.github.akarkin1.auth;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface PermissionsService {

  /**
   * Every known user with their permissions: {@code USER} records (id + username) and
   * {@code PENDING_USER} records (username only, {@link TgUser#pending()}).
   */
  Map<TgUser, List<Permission>> getUsers();

  /**
   * Replaces the user's permissions: a user with an id is kept in a {@code USER} record (the
   * username attribute is the given one, or the stored one when null), a user without an id in a
   * {@code PENDING_USER} record keyed by the username. Null or empty permissions delete that record.
   */
  void updateUserPermissions(TgUser user, Set<Permission> permissions);

  default void deleteUser(TgUser user) {
    this.updateUserPermissions(user, null);
  }

  /** Replaces the user's permissions with the union of the roles' permissions. */
  default void assignRolesToUser(TgUser user, Set<UserRole> roles) {
    Set<Permission> allPermissions = new HashSet<>();
    for (UserRole role : roles) {
      allPermissions.addAll(RoleSet.permissions(role));
    }

    this.updateUserPermissions(user, allPermissions);
  }

}
