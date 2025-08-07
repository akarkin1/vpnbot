package org.github.akarkin1.auth.s3;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.UserEntitlements;
import org.github.akarkin1.config.YamlApplicationConfiguration.AuthConfiguration;

import java.util.Collection;
import java.util.List;
import java.util.Map;

@RequiredArgsConstructor
public class CachingEntitlementsService implements EntitlementsService {

  private final AuthConfiguration authConfig;
  private final EntitlementsService delegate;
  private Map<String, List<UserEntitlements.Entitlement>> cachedPermissions;

  @Override
  public Map<String, List<UserEntitlements.Entitlement>> getUserEntitlements() {
    if (!authConfig.isCacheUserPermissions()) {
      return delegate.getUserEntitlements();
    }

    if (cachedPermissions == null) {
      cachedPermissions = delegate.getUserEntitlements();
    }

    return cachedPermissions;
  }

  @Override
  public void updateUserEntitlements(String tgUsername, Collection<UserEntitlements.Entitlement> newEntitlements) {
    delegate.updateUserEntitlements(tgUsername, newEntitlements);
  }
}
