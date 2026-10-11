package org.github.akarkin1.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.config.YamlApplicationConfiguration.AuthConfiguration;

import java.util.List;

// This should be enough for a TG bot, provided that communication between TG Server and lambda is
// secured with SSL, and no other connections are allowed by a SecurityGroup,
// apart from TG API Servers.
/**
 * Answers from the user's {@code USER} record, looked up by user id. Read-only: a user known by a
 * {@code PENDING_USER} record only has no access until {@link PendingUserActivator} has turned it
 * into a {@code USER} record, which happens at the start of the request.
 */
@Log4j2
@RequiredArgsConstructor
public class WhiteListAuthorizer implements Authorizer {

  private final AuthConfiguration config;
  private final PermissionsService permissionsService;

  @Override
  public boolean hasPermission(TgUser user, Permission permission) {
    log.debug("auth debug: user={}, action={}, isWhiteListEnabled={}",
              user, permission, config.isEnabled());
    if (!Boolean.TRUE.equals(config.isEnabled())) {
      return true;
    }
    if (user == null || user.id() == null) {
      return false;
    }

    List<Permission> permissions = permissionsService.getUsers().get(user);
    return permissions != null
           && (permissions.contains(Permission.ROOT_ACCESS) || permissions.contains(permission));
  }

}
