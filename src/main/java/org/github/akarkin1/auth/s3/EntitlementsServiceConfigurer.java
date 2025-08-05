package org.github.akarkin1.auth.s3;

import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import org.github.akarkin1.s3.S3ConfigManager;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

import java.net.URI;

public class EntitlementsServiceConfigurer {

  public EntitlementsService configure() {
    S3Configuration s3 = ConfigManager.getApplicationYaml().getS3();
    S3ConfigManager s3ConfigManager = S3ConfigManager.create(s3, buildS3Client());
    S3EntitlementsService s3PermissionsService = new S3EntitlementsService(s3ConfigManager, s3);

    return new CachingEntitlementsService(s3PermissionsService);
  }

  private S3Client buildS3Client() {
    if (!ConfigManager.isTestEnvironment()) {
      return S3Client.create();
    }

    return S3Client.builder()
      .endpointOverride(ConfigManager.getLocalStackS3Endpoint())
      .forcePathStyle(true)
      .region(Region.US_EAST_1)
      .credentialsProvider(StaticCredentialsProvider.create(
        AwsBasicCredentials.create("test", "test")))
      .build();
  }

}
