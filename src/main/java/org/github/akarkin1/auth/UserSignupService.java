package org.github.akarkin1.auth;

import java.util.*;
import java.util.stream.Stream;

import static org.github.akarkin1.auth.ServiceRole.*;
import static org.github.akarkin1.auth.UserEntitlements.*;

public interface UserSignupService {

  void updateUserEntitlements(String tgUsername, Collection<Entitlement> newEntitlements);

  default void deleteUser(String tgUsername) {
    this.updateUserEntitlements(tgUsername, null);
  }

  default void assignRolesToUser(String tgUsername, Set<ServiceRole> serviceRoles) {
    List<Entitlement> entitlements = new ArrayList<>();
    for (ServiceRole serviceRole : serviceRoles) {
      Stream<Permission> rolePermissions = switch (serviceRole.getRole()) {
        case READ_ONLY -> Stream.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES);
        case USER_ADMIN -> Stream.of(Permission.USER_MANAGEMENT);
        case NODE_ADMIN -> Stream.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES,
                                      Permission.RUN_NODES);
      };
      rolePermissions.forEach(perm ->
        entitlements.add(new Entitlement(serviceRole.getServiceName(), perm)));
    }

    this.updateUserEntitlements(tgUsername, entitlements);
  }

  default Map<Role, List<Permission>> describeRoles() {
    return Map.of(
            Role.READ_ONLY, List.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES),
            Role.USER_ADMIN, List.of(Permission.USER_MANAGEMENT),
            Role.NODE_ADMIN, List.of(Permission.SUPPORTED_REGIONS, Permission.LIST_NODES,
                                     Permission.RUN_NODES)
    );
  }

}
