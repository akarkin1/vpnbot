package org.github.akarkin1.auth;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.config.YamlApplicationConfiguration.AuthConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.TGUsersWhiteList;

import java.util.List;
import java.util.Map;

// This should be enough for a TG bot, provided that communication between TG Server and lambda is
// secured with SSL, and no other connections are allowed by a SecurityGroup,
// apart from TG API Servers.
@RequiredArgsConstructor
public class WhiteListAuthenticator implements Authenticator {

  private final AuthConfiguration config;

  @Override
  public boolean isAllowed(String tgUsername, UserAction action) {
    if (!Boolean.TRUE.equals(config.isWhiteListEnabled())) {
      return true;
    }

    TGUsersWhiteList whiteList = config.getTgusersWhiteList();
    Map<String, List<UserAction>> usersMap = whiteList.getUserActions();

    return usersMap.containsKey(tgUsername)
           && usersMap.get(tgUsername).contains(action);
  }
}
