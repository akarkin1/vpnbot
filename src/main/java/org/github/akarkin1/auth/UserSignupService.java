package org.github.akarkin1.auth;

import java.util.List;

public interface UserSignupService {

  void addPermissionsTo(String tgUsername, List<UserPermission> actions);

  default void addReadOnlyPermissionsTo(String tgUsername) {
    this.addPermissionsTo(tgUsername, List.of(UserPermission.LIST_NODES, UserPermission.SUPPORTED_REGIONS));
  }

  default void addAdminPermissions(String tgUsername, List<UserPermission> actions) {
    this.addPermissionsTo(tgUsername, List.of(UserPermission.LIST_NODES, UserPermission.SUPPORTED_REGIONS,
                                              UserPermission.RUN_NODES));
  }

}
