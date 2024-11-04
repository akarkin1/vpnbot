package org.github.akarkin1.auth.s3;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Permission;

import java.util.List;
import java.util.Map;
import java.util.Set;

@RequiredArgsConstructor
public class CachingPermissionsService implements PermissionsService {

  private final PermissionsService delegate;
  private Map<String, List<Permission>> cachedPermissions;

  @Override
  public Map<String, List<Permission>> getUserPermissions() {
    if (cachedPermissions == null) {
      cachedPermissions = delegate.getUserPermissions();
    }

    return cachedPermissions;
  }

  @Override
  public void updateUserPermissions(String tgUsername, Set<Permission> actions) {
    // invalidate cache
    cachedPermissions = null;
    delegate.updateUserPermissions(tgUsername, actions);
  }

}
