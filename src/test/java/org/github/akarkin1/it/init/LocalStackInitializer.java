package org.github.akarkin1.it.init;

import lombok.RequiredArgsConstructor;
import org.testcontainers.containers.localstack.LocalStackContainer;
import org.testcontainers.containers.localstack.LocalStackContainer.EnabledService;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;

import java.net.URI;

@RequiredArgsConstructor
public abstract class LocalStackInitializer<C> implements TestContainerInitializer<LocalStackContainer> {
  protected C awsClient;

  public void initialize(LocalStackContainer container) {
    AwsBasicCredentials awsCreds = AwsBasicCredentials.create(container.getAccessKey(),
                                                              container.getSecretKey());
    awsClient = buildAwsClient(container.getEndpointOverride(getService()), awsCreds, container.getRegion());

    createResources(awsClient);
    populateCommonProperties(container);
    populateAdditionalProperties();
  }

  protected void populateAdditionalProperties() {
  }

  protected abstract C buildAwsClient(URI endpointOverride, AwsBasicCredentials awsCreds,
                                      String region);

  protected abstract EnabledService getService();

  protected abstract void createResources(C awsClient);

  private void populateCommonProperties(LocalStackContainer container) {
    System.setProperty("localstack.%s.endpoint".formatted(getService().getName()),
                       container.getEndpointOverride(getService()).toString());
  }

  public C getClient() {
    return awsClient;
  }
}
