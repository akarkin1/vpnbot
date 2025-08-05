package org.github.akarkin1.e2e;

import org.testcontainers.containers.localstack.LocalStackContainer.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

public final class S3Initializer extends LocalStackInitializer<S3Client> {

  public static final String TEST_CONFIG_BUCKET = "test-config-bucket";

  @Override
  protected S3Client buildAwsClient(URI endpointOverride, AwsBasicCredentials awsCreds,
                                    String region) {
    return S3Client.builder()
      .endpointOverride(endpointOverride)
      .forcePathStyle(true)
      .credentialsProvider(StaticCredentialsProvider.create(awsCreds))
      .region(Region.of(region))
      .build();
  }

  @Override
  protected Service getService() {
    return Service.S3;
  }

  @Override
  protected void createResources(S3Client awsClient) {
    awsClient.createBucket(builder -> builder.bucket(TEST_CONFIG_BUCKET));
  }

  @Override
  protected void populateAdditionalProperties() {
    System.setProperty("s3.config.bucket", TEST_CONFIG_BUCKET);
  }
}
