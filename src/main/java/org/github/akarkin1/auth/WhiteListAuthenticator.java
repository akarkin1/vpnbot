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
public class WhiteListAuthenticator implements Authenticator {

  // ToDo: File user registration is inconvenient, add a command
  private final AuthConfiguration config;

  @Override
  public boolean isAllowed(String tgUsername, UserAction action) {
    log.debug("auth debug: username='{}', action={}, isWhiteListEnabled={}",
              tgUsername, action, config.isWhiteListEnabled());
    if (!Boolean.TRUE.equals(config.isWhiteListEnabled())) {
      return true;
    }

    Map<String, List<UserAction>> usersMap = config.getTgusersWhiteList();
    log.debug("usersMap: {}", usersMap);

    return usersMap.containsKey(tgUsername)
           && usersMap.get(tgUsername).contains(action);
  }
}
