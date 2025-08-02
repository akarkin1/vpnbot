package org.github.akarkin1.auth;

import lombok.experimental.UtilityClass;

import java.util.List;
import java.util.Map;

@UtilityClass
public class EntitlementUtil {
  public static boolean hasUserPermission(String tgUsername,
                                          Map<String, List<UserEntitlements.Entitlement>> userEntitlements,
                                          Permission permission) {
    return userEntitlements.get(tgUsername)
        .stream()
        .anyMatch(entitlement -> entitlement.getPermission().equals(permission));
  }
}
