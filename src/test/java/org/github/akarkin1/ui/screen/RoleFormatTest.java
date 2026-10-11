package org.github.akarkin1.ui.screen;

import org.github.akarkin1.auth.Permission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * §4.4: the roles whose permission sets are covered, root as {@code ${ui.role.root}}, otherwise
 * the permission names. Asserts on the message keys, not on translated text.
 */
class RoleFormatTest {

  private static final String NODE_ADMIN = "${ui.role.node-admin}";
  private static final String READ_ONLY = "${ui.role.read-only}";
  private static final String USER_ADMIN = "${ui.role.user-admin}";
  private static final String ROOT = "${ui.role.root}";

  @Test
  @DisplayName("4 AC-7: the node-admin permissions describe as node admin and read-only (both permission sets covered)")
  void nodeAdmin() {
    String roles = RoleFormat.describe(List.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES,
                                               Permission.RUN_NODES));

    assertTrue(roles.contains(NODE_ADMIN), roles);
    assertTrue(roles.contains(READ_ONLY), roles);
    assertFalse(roles.contains(USER_ADMIN), roles);
    assertFalse(roles.contains(ROOT), roles);
  }

  @Test
  @DisplayName("4 AC-7: the read-only permissions describe as read-only only")
  void readOnly() {
    String roles = RoleFormat.describe(List.of(Permission.LIST_NODES, Permission.SUPPORTED_REGIONS));

    assertEquals(READ_ONLY, roles);
  }

  @Test
  @DisplayName("4 AC-7: USER_MANAGEMENT describes as user admin")
  void userAdmin() {
    assertEquals(USER_ADMIN, RoleFormat.describe(List.of(Permission.USER_MANAGEMENT)));
  }

  @Test
  @DisplayName("4 AC-7: read-only plus user admin names both roles, joined by \", \"")
  void twoRoles() {
    String roles = RoleFormat.describe(List.of(Permission.USER_MANAGEMENT, Permission.LIST_NODES,
                                               Permission.SUPPORTED_REGIONS));

    assertTrue(roles.equals(READ_ONLY + ", " + USER_ADMIN) || roles.equals(USER_ADMIN + ", " + READ_ONLY),
               roles);
  }

  @Test
  @DisplayName("4 AC-7: ROOT_ACCESS describes as root only, whatever else the user has")
  void root() {
    assertEquals(ROOT, RoleFormat.describe(List.of(Permission.ROOT_ACCESS)));
    assertEquals(ROOT, RoleFormat.describe(List.of(Permission.ROOT_ACCESS, Permission.RUN_NODES,
                                                   Permission.LIST_NODES,
                                                   Permission.SUPPORTED_REGIONS)));
  }

  @Test
  @DisplayName("4 AC-7: permissions that cover no role are shown by their names")
  void permissionNamesWithoutRole() {
    String roles = RoleFormat.describe(List.of(Permission.RUN_NODES));

    assertTrue(roles.contains("RUN_NODES"), roles);
    assertFalse(roles.contains("${"), roles);
  }

  @Test
  @DisplayName("4 AC-7: DELETE_USERS alone is shown by its name")
  void deleteUsersByName() {
    String roles = RoleFormat.describe(List.of(Permission.DELETE_USERS));

    assertTrue(roles.contains("DELETE_USERS"), roles);
  }

  @Test
  @DisplayName("4 AC-7: the description holds no % (it may end up in a button label or a template)")
  void noPercent() {
    for (Permission permission : Permission.values()) {
      assertFalse(RoleFormat.describe(List.of(permission)).contains("%"), permission.name());
    }
  }

}
