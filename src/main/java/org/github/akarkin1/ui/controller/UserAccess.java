package org.github.akarkin1.ui.controller;

import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.TgUser;

import java.util.Arrays;
import java.util.List;

/** Access checks of the access request and Users screens; root implies every check. */
final class UserAccess {

  private UserAccess() {
  }

  /** Any permission at all; a user without one may request access. */
  static boolean hasAccess(Authorizer authorizer, TgUser user) {
    return Arrays.stream(Permission.values())
        .anyMatch(permission -> authorizer.hasPermission(user, permission));
  }

  static boolean canManageUsers(Authorizer authorizer, TgUser user) {
    return hasAny(authorizer, user, Permission.USER_MANAGEMENT);
  }

  static boolean canDeleteUsers(Authorizer authorizer, TgUser user) {
    return hasAny(authorizer, user, Permission.DELETE_USERS);
  }

  /** Admins of the access requests: {@code USER_MANAGEMENT} or root. */
  static boolean isUserAdmin(List<Permission> permissions) {
    return permissions.contains(Permission.USER_MANAGEMENT)
           || permissions.contains(Permission.ROOT_ACCESS);
  }

  private static boolean hasAny(Authorizer authorizer, TgUser user, Permission permission) {
    return authorizer.hasPermission(user, permission)
           || authorizer.hasPermission(user, Permission.ROOT_ACCESS);
  }

}
