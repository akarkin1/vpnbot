package org.github.akarkin1.tailscale;

import org.github.akarkin1.config.CachedS3TaskConfigService;
import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.S3TaskConfigService;
import org.github.akarkin1.config.TaskConfigService;
import org.github.akarkin1.config.YamlApplicationConfiguration;
import org.github.akarkin1.ecs.EcsClientPool;
import org.github.akarkin1.ecs.EcsManager;
import org.github.akarkin1.ecs.EcsManagerImpl;

public class TailscaleEcsNodeServiceConfigurer implements TailscaleNodeServiceConfigurer {

  @Override
  public TailscaleNodeService configure() {
    YamlApplicationConfiguration appConfig = ConfigManager.getApplicationYaml();

    S3TaskConfigService s3TaskConfigService = S3TaskConfigService.create(appConfig.getS3());
    TaskConfigService cachedConigService = new CachedS3TaskConfigService(s3TaskConfigService);
    EcsClientPool clientPool = new EcsClientPool();
    EcsManager ecsManager = new EcsManagerImpl(cachedConigService, clientPool, appConfig.getEcs(),
                                               appConfig.getAws().getRegionCities());

    return new TailscaleEcsNodeService(ecsManager, appConfig.getEcs(), appConfig.getAws());
  }

}
