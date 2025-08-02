package org.github.akarkin1.auth;

import java.util.List;
import java.util.Map;

public interface UserEntitlementsProvider {

  Map<String, List<UserEntitlements.Entitlement>> getUserEntitlements();

}
