package org.github.akarkin1.auth;

import java.util.List;

public interface UserSignupService {

  void addPermissionsTo(String tgUsername, List<UserAction> actions);

  default void addReadOnlyPermissionsTo(String tgUsername) {
    this.addPermissionsTo(tgUsername, List.of(UserAction.LIST_NODES, UserAction.SUPPORTED_REGIONS));
  }

  default void addAdminPermissions(String tgUsername, List<UserAction> actions) {
    this.addPermissionsTo(tgUsername, List.of(UserAction.LIST_NODES, UserAction.SUPPORTED_REGIONS,
                                              UserAction.RUN_NODES));
  }

}
