package org.github.akarkin1.ui.screen;

import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.RoleSet;
import org.github.akarkin1.auth.UserRole;
import org.github.akarkin1.ui.UiAction;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Role names as template text (the ui.role keys, translated with the screen). The result
 * holds no user data and no {@code %}, so it goes into templates and button labels as is.
 */
public final class RoleFormat {

  private static final String SEPARATOR = ", ";
  private static final String NONE = "—";

  private RoleFormat() {
  }

  /**
   * Root → {@code ${ui.role.root}}; otherwise the roles whose permissions the user has; when no
   * role is covered, the permission names.
   */
  public static String describe(Collection<Permission> permissions) {
    if (permissions.contains(Permission.ROOT_ACCESS)) {
      return "${ui.role.root}";
    }
    Set<UserRole> roles = RoleSet.coveredBy(permissions);
    if (!roles.isEmpty()) {
      return names(roles);
    }
    if (permissions.isEmpty()) {
      return NONE;
    }
    return permissions.stream()
        .map(Permission::name)
        .collect(Collectors.joining(SEPARATOR));
  }

  /** The roles' names in display order, joined by ", ". */
  public static String names(Set<UserRole> roles) {
    return RoleSet.ROLES.stream()
        .filter(roles::contains)
        .map(RoleFormat::name)
        .collect(Collectors.joining(SEPARATOR));
  }

  public static String name(UserRole role) {
    return switch (role) {
      case NODE_ADMIN -> "${ui.role.node-admin}";
      case READ_ONLY -> "${ui.role.read-only}";
      case USER_ADMIN -> "${ui.role.user-admin}";
    };
  }

  /**
   * The role checkboxes: {@code [✅|☐ Node admin] [✅|☐ Read-only]} and {@code [✅|☐ User admin]},
   * each with the action for that role.
   */
  static List<List<Button>> toggleRows(Set<UserRole> ticked, Function<UserRole, UiAction> action) {
    Function<UserRole, Button> toggle = role -> Button.action(
        (ticked.contains(role) ? "✅ " : "☐ ") + name(role), action.apply(role));
    return List.of(List.of(toggle.apply(UserRole.NODE_ADMIN), toggle.apply(UserRole.READ_ONLY)),
                   List.of(toggle.apply(UserRole.USER_ADMIN)));
  }

}
