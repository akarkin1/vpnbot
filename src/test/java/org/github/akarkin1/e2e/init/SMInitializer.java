package org.github.akarkin1.e2e.init;

import org.testcontainers.containers.localstack.LocalStackContainer.EnabledService;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;

import java.net.URI;

public final class SMInitializer extends LocalStackInitializer<SecretsManagerClient> {

  public static final String TEST_TG_TOKEN_SECRET_ID = "ecs-tgbot/tg-bot-secret-token";
  public static final String TEST_TG_SECRET_TOKEN = "tg-bot-secret-token";

  @Override
  protected SecretsManagerClient buildAwsClient(URI endpointOverride,
                                                AwsBasicCredentials awsCreds, String region) {
    return SecretsManagerClient.builder()
      .endpointOverride(endpointOverride)
      .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
      .region(Region.of(region))
      .build();
  }

  @Override
  protected EnabledService getService() {
    return Service.SECRETSMANAGER;
  }

  @Override
  protected void createResources(SecretsManagerClient awsClient) {
    try {
      awsClient.createSecret(CreateSecretRequest.builder()
                               .name(TEST_TG_TOKEN_SECRET_ID)
                               .secretString(TEST_TG_SECRET_TOKEN)
                               .build());
    } catch (Exception e) {
      // ignore if already exists
    }
  }

  @Override
  protected void populateAdditionalProperties() {
    System.setProperty("bot.secret.token.id", TEST_TG_TOKEN_SECRET_ID);
  }
}
