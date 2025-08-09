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
public class WhiteListAuthorizer implements Authorizer {

  private final AuthConfiguration config;
  private final UserEntitlementsProvider permissionsProvider;
  private final ConfigManager configManager;

  public WhiteListAuthorizer(AuthConfiguration config, UserEntitlementsProvider permissionsProvider, ConfigManager configManager) {
    this.config = config;
    this.permissionsProvider = permissionsProvider;
    this.configManager = configManager;
  }

  @Override
  public boolean hasPermission(String tgUsername, Permission permission) {
    log.debug("auth debug: username='{}', action={}, isWhiteListEnabled={}",
              tgUsername, permission, config.isEnabled());
    if (!config.isEnabled()) {
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
    if (!config.isEnabled()) {
      return configManager.getSupportedServices();
    }

    if (tgUsername == null) {
      return Collections.emptySet();
    }

    if (permission == Permission.ROOT_ACCESS) {
      return configManager.getSupportedServices();
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
    if (!config.isEnabled()) {
      return configManager.getSupportedServices();
    }

    if (username == null) {
      return Collections.emptySet();
    }

    Set<String> allServices = configManager.getSupportedServices();
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
