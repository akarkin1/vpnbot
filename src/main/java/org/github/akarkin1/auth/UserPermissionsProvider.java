package org.github.akarkin1.auth;

import java.util.List;
import java.util.Map;

public interface UserPermissionsProvider {

  Map<String, List<UserAction>> getUserPermissions();

}
