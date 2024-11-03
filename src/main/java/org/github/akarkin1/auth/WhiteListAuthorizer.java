package org.github.akarkin1.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.config.YamlApplicationConfiguration.AuthConfiguration;

import java.util.List;
import java.util.Map;

// This should be enough for a TG bot, provided that communication between TG Server and lambda is
// secured with SSL, and no other connections are allowed by a SecurityGroup,
// apart from TG API Servers.
@Log4j2
@RequiredArgsConstructor
public class WhiteListAuthorizer implements Authorizer {

  private final AuthConfiguration config;
  private final UserPermissionsProvider permissionsProvider;

  @Override
  public boolean hasPermission(String tgUsername, UserPermission permission) {
    log.debug("auth debug: username='{}', action={}, isWhiteListEnabled={}",
              tgUsername, permission, config.isEnabled());
    if (!Boolean.TRUE.equals(config.isEnabled())) {
      return true;
    }

    Map<String, List<UserPermission>> usersMap = permissionsProvider.getUserPermissions();
    log.debug("usersMap: {}", usersMap);

    return usersMap.containsKey(tgUsername)
           && (usersMap.get(tgUsername).contains(UserPermission.ROOT_ACCESS)
               || usersMap.get(tgUsername).contains(permission));
  }
}
