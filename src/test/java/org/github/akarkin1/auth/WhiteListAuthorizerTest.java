package org.github.akarkin1.auth;

import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.YamlApplicationConfiguration.AuthConfiguration;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WhiteListAuthorizerTest {

  @Test
  void isAllowed() {
    AuthConfiguration config = ConfigManager.getApplicationYaml().getAuth();
    WhiteListAuthorizer authenticator = new WhiteListAuthorizer(config);

    assertTrue(authenticator.isAllowed("karkin_ai", UserAction.RUN_NODES));
    assertTrue(authenticator.isAllowed("karkin_ai", UserAction.ADMIN));
  }
}