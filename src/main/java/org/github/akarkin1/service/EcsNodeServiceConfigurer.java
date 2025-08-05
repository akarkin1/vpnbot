package org.github.akarkin1.service;

import org.github.akarkin1.config.CachedS3TaskConfigService;
import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.S3TaskConfigService;
import org.github.akarkin1.config.TaskConfigService;
import org.github.akarkin1.config.YamlApplicationConfiguration;
import org.github.akarkin1.config.YamlApplicationConfiguration.S3Configuration;
import org.github.akarkin1.ec2.Ec2ClientPool;
import org.github.akarkin1.ecs.EcsClientPool;
import org.github.akarkin1.ecs.EcsManager;
import org.github.akarkin1.ecs.EcsManagerImpl;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Configurer for the EcsNodeService.
 */
public class EcsNodeServiceConfigurer {

  /**
   * Configure and create an EcsNodeService.
   *
   * @return the configured EcsNodeService
   */
  public NodeService configure() {
    YamlApplicationConfiguration appConfig = ConfigManager.getApplicationYaml();

    S3Configuration s3Config = appConfig.getS3();
    S3TaskConfigService s3TaskConfigService = S3TaskConfigService.create(s3Config,
                                                                         buildS3Client());
    TaskConfigService cachedConfigService = new CachedS3TaskConfigService(s3TaskConfigService, s3Config);
    EcsClientPool ecsClientPool = new EcsClientPool();
    Ec2ClientPool ec2ClientPool = new Ec2ClientPool();
    EcsManager ecsManager = new EcsManagerImpl(cachedConfigService, ecsClientPool,
                                             ec2ClientPool, appConfig.getEcs(),
                                             appConfig.getAws().getRegionCities());

    return new EcsNodeService(ecsManager, appConfig.getEcs(), appConfig.getAws(), s3Config);
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