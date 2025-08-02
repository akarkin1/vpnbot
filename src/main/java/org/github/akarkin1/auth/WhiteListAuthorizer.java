package org.github.akarkin1.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.YamlApplicationConfiguration.AuthConfiguration;

import java.util.*;
import java.util.stream.Collectors;

import static org.github.akarkin1.auth.EntitlementUtil.*;

// This should be enough for a TG bot, provided that communication between TG Server and lambda is
// secured with SSL, and no other connections are allowed by a SecurityGroup,
// apart from TG API Servers.
@Log4j2
@RequiredArgsConstructor
public class WhiteListAuthorizer implements Authorizer {

  private final AuthConfiguration config;
  private final UserEntitlementsProvider permissionsProvider;

  @Override
  public boolean hasPermission(String tgUsername, Permission permission) {
    log.debug("auth debug: username='{}', action={}, isWhiteListEnabled={}",
              tgUsername, permission, config.isEnabled());
    if (!Boolean.TRUE.equals(config.isEnabled())) {
      return true;
    }

    if (tgUsername == null) {
      return false;
    }

    Map<String, List<UserEntitlements.Entitlement>> userEntitlements = permissionsProvider.getUserEntitlements();
    log.debug("userEntitlements: {}", userEntitlements);

    return userEntitlements.containsKey(tgUsername)
           && (hasUserPermission(tgUsername, userEntitlements, Permission.ROOT_ACCESS)
               || hasUserPermission(tgUsername, userEntitlements, permission));
  }

  @Override
  public Set<String> getAllowedServices(String tgUsername, Permission permission) {
    if (!Boolean.TRUE.equals(config.isEnabled())) {
      return ConfigManager.getSupportedServices();
    }

    if (tgUsername == null) {
      return Collections.emptySet();
    }

    if (permission == Permission.ROOT_ACCESS) {
      return ConfigManager.getSupportedServices();
    }

    Map<String, List<UserEntitlements.Entitlement>> userEntitlements = permissionsProvider.getUserEntitlements();
    if (!userEntitlements.containsKey(tgUsername)) {
      return Collections.emptySet();
    }

    return userEntitlements.get(tgUsername).stream()
        .filter(userPerm -> userPerm.getPermission().equals(permission))
        .map(UserEntitlements.Entitlement::getService)
        .collect(Collectors.toSet());
  }

  @Override
  public Set<String> getAllowedServices(String username) {
    if (!Boolean.TRUE.equals(config.isEnabled())) {
      return ConfigManager.getSupportedServices();
    }

    if (username == null) {
      return Collections.emptySet();
    }

    Set<String> allServices = ConfigManager.getSupportedServices();
    if (hasPermission(username, Permission.ROOT_ACCESS)) {
      return allServices;
    }

    Map<String, List<UserEntitlements.Entitlement>> userEntitlements = permissionsProvider.getUserEntitlements();
    if (!userEntitlements.containsKey(username)) {
      return Collections.emptySet();
    }

    return userEntitlements.get(username).stream()
        .filter(entitlement -> entitlement.getService() != null && allServices.contains(entitlement.getService()))
        .map(UserEntitlements.Entitlement::getService)
        .collect(Collectors.toSet());
  }

}
