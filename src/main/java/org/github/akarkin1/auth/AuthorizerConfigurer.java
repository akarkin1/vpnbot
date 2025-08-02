package org.github.akarkin1.auth;

import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.YamlApplicationConfiguration.AuthConfiguration;

public class AuthorizerConfigurer {

  public Authorizer configure(UserEntitlementsProvider userEntitlementsProvider) {
    AuthConfiguration authConfig = ConfigManager.getApplicationYaml().getAuth();
    return new WhiteListAuthorizer(authConfig, userEntitlementsProvider);
  }

}
