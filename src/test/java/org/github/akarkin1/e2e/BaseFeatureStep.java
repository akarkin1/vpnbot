package org.github.akarkin1.e2e;

import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.util.stream.Stream;

@Testcontainers
public abstract class BaseFeatureStep {

  @Container
  protected static LocalStackContainer localstack;

  private static final S3Initializer S3_INITIALIZER = new S3Initializer();
  private static final SMInitializer SM_INITIALIZER = new SMInitializer();
  private static final LocalStackInitializer<?>[] LS_INITIALIZERS = {S3_INITIALIZER, SM_INITIALIZER};

  static {
    localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:3.4.0"))
      .withServices(LocalStackContainer.Service.S3, LocalStackContainer.Service.SECRETSMANAGER);
    localstack.start();

    Stream.of(LS_INITIALIZERS)
      .forEach(initializer -> initializer.initialize(localstack));

    System.setProperty("lambda.profile", "local");
  }

  protected final S3Client s3Client;
  protected final SecretsManagerClient secretsManagerClient;

  public BaseFeatureStep() {
    s3Client = S3_INITIALIZER.getClient();
    secretsManagerClient = SM_INITIALIZER.getClient();
  }
}

