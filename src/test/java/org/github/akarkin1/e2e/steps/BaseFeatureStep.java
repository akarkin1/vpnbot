package org.github.akarkin1.e2e.steps;

import org.github.akarkin1.e2e.init.LocalStackInitializer;
import org.github.akarkin1.e2e.init.S3Initializer;
import org.github.akarkin1.e2e.init.SMInitializer;
import org.github.akarkin1.e2e.init.WireMockInitializer;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.wiremock.integrations.testcontainers.WireMockContainer;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

import java.util.stream.Stream;

import static org.github.akarkin1.e2e.init.WireMockInitializer.WIRE_MOCK_PORT;

@Testcontainers
public abstract class BaseFeatureStep {

  private static final WireMockInitializer WM_INITIALIZER = new WireMockInitializer();

  private static final S3Initializer S3_INITIALIZER = new S3Initializer();
  private static final SMInitializer SM_INITIALIZER = new SMInitializer();

  private static final LocalStackInitializer<?>[] LS_INITIALIZERS = {
    S3_INITIALIZER,
    SM_INITIALIZER
  };

  @Container
  protected static LocalStackContainer localstack;
  @Container
  protected static WireMockContainer wiremock;

  static {

    localstack = new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.7.0"))
      .withServices(Service.S3, Service.SECRETSMANAGER);
    localstack.start();

    Stream.of(LS_INITIALIZERS)
      .forEach(initializer -> initializer.initialize(localstack));

    System.setProperty("lambda.profile", "local");

    wiremock = new WireMockContainer("wiremock/wiremock:3.13.1")
      .withExposedPorts(WIRE_MOCK_PORT)
      .withEnv("WIREMOCK_OPTIONS", "--disable-banner");
    wiremock.start();

    WM_INITIALIZER.initialize(wiremock);
  }

  protected final S3Client s3Client;
  protected final SecretsManagerClient secretsManagerClient;

  public BaseFeatureStep() {
    s3Client = S3_INITIALIZER.getClient();
    secretsManagerClient = SM_INITIALIZER.getClient();
  }

}

