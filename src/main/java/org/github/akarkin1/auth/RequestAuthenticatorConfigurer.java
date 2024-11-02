package org.github.akarkin1.auth;


import org.github.akarkin1.config.ConfigManager;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

public class RequestAuthenticatorConfigurer {

  public RequestAuthenticator configure() {
    SecretsManagerClient secretsManager = SecretsManagerClient.create();
    String secretTokenId = ConfigManager.getSecretTokenId();
    return new SecretManagerRequestAuthenticator(secretsManager, secretTokenId);
  }

}
