package org.github.akarkin1.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.config.YamlApplicationConfiguration.AuthConfiguration;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

// This should be enough for a TG bot, provided that communication between TG Server and lambda is
// secured with SSL, and no other connections are allowed by a SecurityGroup,
// apart from TG API Servers.
/**
 * Answers from the user's {@code USER} record (looked up by user id). A user without one who was
 * pre-registered by username ({@code PENDING_USER}) is activated on the first request: the
 * {@code USER} record is written with the pending permissions, then the pending record is deleted.
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

    List<Permission> permissions = permissionsOf(user);
    return permissions != null
           && (permissions.contains(Permission.ROOT_ACCESS) || permissions.contains(permission));
  }

  private List<Permission> permissionsOf(TgUser user) {
    Map<TgUser, List<Permission>> users = permissionsService.getUsers();
    log.debug("users: {}", users);

    List<Permission> permissions = users.get(user);
    if (permissions != null || user.username() == null) {
      return permissions;
    }

    TgUser pendingKey = new TgUser(null, user.username());
    for (Entry<TgUser, List<Permission>> entry : users.entrySet()) {
      if (entry.getKey().equals(pendingKey)) {
        activate(user, entry.getKey(), entry.getValue());
        return entry.getValue();
      }
    }
    return null;
  }

  private void activate(TgUser user, TgUser pendingUser, List<Permission> permissions) {
    log.info("Activating pending user @{} as user {}", pendingUser.username(), user.id());
    try {
      permissionsService.updateUserPermissions(user, new HashSet<>(permissions));
      permissionsService.deleteUser(pendingUser);
    } catch (RuntimeException e) {
      // the pending record still grants the permissions; the activation is retried next time
      log.error("Failed to activate pending user @{} as user {}", pendingUser.username(),
                user.id(), e);
    }
  }

}
