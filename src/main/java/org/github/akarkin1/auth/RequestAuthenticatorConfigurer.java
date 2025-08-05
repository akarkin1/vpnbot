package org.github.akarkin1.auth;


import org.github.akarkin1.config.ConfigManager;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.net.URI;

public class RequestAuthenticatorConfigurer {

  public RequestAuthenticator configure() {
    SecretsManagerClient secretsManager = buildSecretManager();
    String secretTokenId = ConfigManager.getSecretTokenId();
    return new SecretManagerRequestAuthenticator(secretsManager, secretTokenId);
  }

  private SecretsManagerClient buildSecretManager() {
    if (!ConfigManager.isTestEnvironment()){
      return SecretsManagerClient.create();
    }

    return SecretsManagerClient.builder()
      .endpointOverride(ConfigManager.getLocalStackSMEndpoint())
      .region(Region.US_EAST_1)
      .credentialsProvider(StaticCredentialsProvider.create(
          AwsBasicCredentials.create("test", "test")))
      .build();
  }
}
