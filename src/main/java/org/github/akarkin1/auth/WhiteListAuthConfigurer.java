package org.github.akarkin1.auth;

import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.YamlApplicationConfiguration.AuthConfiguration;

public class WhiteListAuthConfigurer implements AuthenticatorConfigurer {

  @Override
  public Authorizer configure() {
    AuthConfiguration authConfig = ConfigManager.getApplicationYaml().getAuth();
    return new WhiteListAuthorizer(authConfig);
  }

}
