package org.github.akarkin1.auth;

import java.util.Set;

public interface Authorizer {

  boolean hasPermission(String tgUsername, Permission permission);

  Set<String> getAllowedServices(String tgUsername, Permission permission);

  default boolean hasPermissionForService(String tgUsername, Permission permission, String serviceName) {
    return getAllowedServices(tgUsername, permission).contains(serviceName);
  }

  Set<String> getAllowedServices(String username);
}
