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
import org.github.akarkin1.metrics.RequestMetrics;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TailscaleEcsNodeServiceConfigurer {

  private static final int ECS_THREADS = 8;

  public TailscaleNodeService configure(RequestMetrics metrics) {
    YamlApplicationConfiguration appConfig = ConfigManager.getApplicationYaml();

    S3Configuration s3Config = appConfig.getS3();
    S3TaskConfigService s3TaskConfigService = S3TaskConfigService.create(s3Config, metrics);
    TaskConfigService cachedConfigService = new CachedS3TaskConfigService(
        s3TaskConfigService, ConfigManager.getConfigCacheTtl(), Clock.systemUTC());
    EcsClientPool ecsClientPool = new EcsClientPool();
    Ec2ClientPool ec2ClientPool = new Ec2ClientPool();
    ExecutorService executor = Executors.newFixedThreadPool(
        ECS_THREADS, Thread.ofPlatform().daemon().factory());
    EcsManager ecsManager = new EcsManagerImpl(cachedConfigService, ecsClientPool,
                                               ec2ClientPool, appConfig.getEcs(),
                                               appConfig.getAws().getRegionCities(),
                                               executor, metrics);

    return new TailscaleEcsNodeService(ecsManager, appConfig.getEcs(), appConfig.getAws());
  }

}
