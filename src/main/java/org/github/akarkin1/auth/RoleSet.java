package org.github.akarkin1.auth;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * Sets of {@link UserRole}s as a bitmask for callback data ({@code NODE_ADMIN = 1},
 * {@code READ_ONLY = 2}, {@code USER_ADMIN = 4}), and the permissions each role grants.
 */
public final class RoleSet {

  /** The roles in display order (the order of the role buttons). */
  public static final List<UserRole> ROLES = List.of(UserRole.NODE_ADMIN, UserRole.READ_ONLY,
                                                     UserRole.USER_ADMIN);
  /** The mask of all roles. */
  public static final int ALL = 7;

  private RoleSet() {
  }

  public static int encode(Set<UserRole> roles) {
    int mask = 0;
    for (UserRole role : roles) {
      mask |= bit(role);
    }
    return mask;
  }

  /** @throws IllegalArgumentException for a mask outside {@code 0..7} */
  public static Set<UserRole> decode(int mask) {
    if (!isValid(mask)) {
      throw new IllegalArgumentException("Invalid role mask: " + mask);
    }
    Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
    for (UserRole role : UserRole.values()) {
      if ((mask & bit(role)) != 0) {
        roles.add(role);
      }
    }
    return roles;
  }

  public static boolean isValid(int mask) {
    return mask >= 0 && mask <= ALL;
  }

  /** The permissions the role grants. */
  public static List<Permission> permissions(UserRole role) {
    return switch (role) {
      case READ_ONLY -> List.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES);
      case USER_ADMIN -> List.of(Permission.USER_MANAGEMENT);
      case NODE_ADMIN -> List.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES,
                                 Permission.RUN_NODES);
    };
  }

  /** The roles whose permissions are all among the given ones. */
  public static Set<UserRole> coveredBy(Iterable<Permission> permissions) {
    Set<Permission> granted = EnumSet.noneOf(Permission.class);
    permissions.forEach(granted::add);
    Set<UserRole> roles = EnumSet.noneOf(UserRole.class);
    for (UserRole role : ROLES) {
      if (granted.containsAll(permissions(role))) {
        roles.add(role);
      }
    }
    return roles;
  }

  private static int bit(UserRole role) {
    return switch (role) {
      case NODE_ADMIN -> 1;
      case READ_ONLY -> 2;
      case USER_ADMIN -> 4;
    };
  }

}
