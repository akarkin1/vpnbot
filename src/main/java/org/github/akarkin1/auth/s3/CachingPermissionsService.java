package org.github.akarkin1.auth.s3;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.UserPermission;

import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class CachingPermissionsService implements PermissionsService {

  private final PermissionsService delegate;
  private Map<String, List<UserPermission>> cachedPermissions;

  @Override
  public Map<String, List<UserPermission>> getUserPermissions() {
    if (cachedPermissions == null) {
      cachedPermissions = delegate.getUserPermissions();
    }

    return cachedPermissions;
  }

  @Override
  public void addPermissionsTo(String tgUsername, List<UserPermission> actions) {
    // invalidate cache
    cachedPermissions = null;
    delegate.addPermissionsTo(tgUsername, actions);
  }

}
