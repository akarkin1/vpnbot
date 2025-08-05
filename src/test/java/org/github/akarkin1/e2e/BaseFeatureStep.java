package org.github.akarkin1.e2e;

import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;
import software.amazon.awssdk.services.secretsmanager.model.CreateSecretRequest;

import java.net.URI;

@Testcontainers
public abstract class BaseFeatureStep {

  protected static final String BUCKET_NAME = "test-bucket";
  protected static final String TEST_SECRET_TOKEN_ID = "ecs-tgbot/tg-bot-secret-token";
  protected static final String TEST_SECRET_TOKEN_VALUE = "tg-bot-secret-token";
  protected static final String TG_BOT_TOKEN = "0123456789:ABC-DEF1234ghIkl-zyx57W2v1u123ew11";

  @Container
  public static LocalStackContainer localstack;

  @Container
  public static TelegramBotApiContainer telegramApi;

  protected static S3Client s3Client;


  static {
    localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4.0"))
      .withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.SECRETSMANAGER);
    localstack.start();
    telegramApi = new TelegramBotApiContainer()
      .withExposedPorts(8081)
      .withEnv("TELEGRAM_LOCAL", "true")
      .withEnv("TELEGRAM_API_ID", System.getProperty("telegram.api.id", "123456789"))
      .withEnv("TELEGRAM_API_HASH",
               System.getProperty("telegram.api.hash", "2fed714ccea3486cbabe99138c517915"))
    ;
    telegramApi.start();
    URI s3Endpoint = localstack.getEndpointOverride(Service.S3);
    s3Client = S3Client.builder()
      .endpointOverride(s3Endpoint)
      .forcePathStyle(true)
      .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
      ))
      .region(Region.of(localstack.getRegion()))
      .build();

    // Initialize Secrets Manager with test secret
    URI smEndpoint = localstack.getEndpointOverride(Service.SECRETSMANAGER);
    SecretsManagerClient secretsManagerClient = SecretsManagerClient.builder()
      .endpointOverride(smEndpoint)
      .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create(localstack.getAccessKey(), localstack.getSecretKey())
      ))
      .region(Region.of(localstack.getRegion()))
      .build();
    try {
      secretsManagerClient.createSecret(CreateSecretRequest.builder()
                                          .name(TEST_SECRET_TOKEN_ID)
                                          .secretString(TEST_SECRET_TOKEN_VALUE)
                                          .build());
    } catch (Exception e) {
      // ignore if already exists
    }

    System.setProperty("lambda.profile", "local");
    System.setProperty("bot.secret.token.id", TEST_SECRET_TOKEN_ID);
    System.setProperty("tg.bot.token", TG_BOT_TOKEN);
    System.setProperty("tg.bot.api.base.url", String.format("http://%s:%s/bot%s/", telegramApi.getHost(), telegramApi.getMappedPort(8081), TG_BOT_TOKEN));
    System.setProperty("s3.config.bucket", BUCKET_NAME);
    System.setProperty("localstack.s3.endpoint", s3Endpoint.toString());
    System.setProperty("localstack.secretmanager.endpoint", smEndpoint.toString());

    try {
      s3Client.createBucket(CreateBucketRequest.builder().bucket(BUCKET_NAME).build());
    } catch (Exception e) {
      // ignore if already exists
    }
  }
}

