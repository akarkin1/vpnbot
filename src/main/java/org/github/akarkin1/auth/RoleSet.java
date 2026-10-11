package org.github.akarkin1.auth;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

  /** @throws IllegalArgumentException for a mask outside {@code 0..7} <br/>
   * <p>Selected Roles Mask Decoding: Dec -> Bin:</p>
   * <pre>
   * 0: 000
   * 1: 001
   * 2: 010
   * 3: 011
   * 4: 100
   * 5: 101
   * 6: 110
   * 7: 111
   * </pre>
   * <p>Role bit mapping (as if a single role was picked only):</p>
   * <pre>
   * NODE_ADMIN -> 1: 001 (SUPPORTED_REGIONS, LIST_NODES, RUN_NODES)
   * READ_ONLY -> 2: 010 (SUPPORTED_REGIONS, LIST_NODES)
   * USER_ADMIN -> 4: 100 (USER_MANAGEMENT)
   * </pre>
   * Conjunction of the user choice mask with a role's bit position tells us whether the role was chosen or not by user.
   * For example, given the user mask {@code 101}, we can deduce that the below roles are selected:
   * <ul>
   *  <li>NODE_ADMIN ({@code 101 & 001 = 001 != 0})</li>
   *  <li>USER_ADMIN ({@code 101 & 100 = 100 != 0}</li>
   * </ul>
   * but not READ_ONLY, because ({@code 101 & 010 = 0})
   */
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

  /** Every role with the permissions it grants, in {@link #ROLES} order (for {@code /describeRoles}). */
  public static Map<UserRole, List<Permission>> describe() {
    Map<UserRole, List<Permission>> roles = new LinkedHashMap<>();
    for (UserRole role : ROLES) {
      roles.put(role, permissions(role));
    }
    return Collections.unmodifiableMap(roles);
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
