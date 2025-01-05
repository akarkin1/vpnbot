package org.github.akarkin1.tailscale;

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

public class TailscaleEcsNodeServiceConfigurer {

  public TailscaleNodeService configure() {
    YamlApplicationConfiguration appConfig = ConfigManager.getApplicationYaml();

    S3Configuration s3Config = appConfig.getS3();
    S3TaskConfigService s3TaskConfigService = S3TaskConfigService.create(s3Config);
    TaskConfigService cachedConfigService = new CachedS3TaskConfigService(s3TaskConfigService, s3Config);
    EcsClientPool ecsClientPool = new EcsClientPool();
    Ec2ClientPool ec2ClientPool = new Ec2ClientPool();
    EcsManager ecsManager = new EcsManagerImpl(cachedConfigService, ecsClientPool,
                                               ec2ClientPool, appConfig.getEcs(),
                                               appConfig.getAws().getRegionCities());

    return new TailscaleEcsNodeService(ecsManager, appConfig.getEcs(), appConfig.getAws());
  }

}
