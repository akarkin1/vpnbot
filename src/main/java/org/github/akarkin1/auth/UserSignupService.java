package org.github.akarkin1.auth;

import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public interface UserSignupService {

  void updateUserPermissions(String tgUsername, Set<Permission> actions);

  default void deleteUser(String tgUsername) {
    this.updateUserPermissions(tgUsername, null);
  }

  default void assignRolesToUser(String tgUsername, Set<UserRole> roles) {
    Set<Permission> allPermissions = new HashSet<>();
    for (UserRole role : roles) {
      EnumSet<Permission> rolePermissions = switch (role) {
        case READ_ONLY -> EnumSet.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES);
        case USER_ADMIN -> EnumSet.of(Permission.USER_MANAGEMENT);
        case NODE_ADMIN -> EnumSet.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES,
                                      Permission.RUN_NODES);
      };
      allPermissions.addAll(rolePermissions);
    }

    this.updateUserPermissions(tgUsername, allPermissions);
  }

  default Map<UserRole, List<Permission>> describeRoles() {
    return Map.of(
        UserRole.READ_ONLY, List.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES),
        UserRole.USER_ADMIN, List.of(Permission.USER_MANAGEMENT),
        UserRole.NODE_ADMIN, List.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES,
                                     Permission.RUN_NODES)
    );
  }

}
