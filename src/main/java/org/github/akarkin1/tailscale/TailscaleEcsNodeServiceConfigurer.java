package org.github.akarkin1.tailscale;

import org.github.akarkin1.config.CachedTaskConfigService;
import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.config.DynamoDbTaskConfigService;
import org.github.akarkin1.config.TaskConfigService;
import org.github.akarkin1.config.YamlApplicationConfiguration;
import org.github.akarkin1.dynamodb.RegionRecord;
import org.github.akarkin1.ec2.Ec2ClientPool;
import org.github.akarkin1.ecs.EcsClientPool;
import org.github.akarkin1.ecs.EcsManager;
import org.github.akarkin1.ecs.EcsManagerImpl;
import org.github.akarkin1.metrics.RequestMetrics;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

import java.time.Clock;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class TailscaleEcsNodeServiceConfigurer {

  private static final int ECS_THREADS = 8;

  public NodeServices configure(DynamoDbTable<RegionRecord> regions, RequestMetrics metrics) {
    YamlApplicationConfiguration appConfig = ConfigManager.getApplicationYaml();

    TaskConfigService configService = configureTaskConfigService(regions, metrics);
    EcsClientPool ecsClientPool = new EcsClientPool();
    Ec2ClientPool ec2ClientPool = new Ec2ClientPool();
    ExecutorService executor = Executors.newFixedThreadPool(
        ECS_THREADS, Thread.ofPlatform().daemon().factory());
    EcsManager ecsManager = new EcsManagerImpl(configService, ecsClientPool,
                                               ec2ClientPool, appConfig.getEcs(),
                                               appConfig.getAws().getRegionCities(),
                                               executor, metrics);

    TailscaleNodeService nodeService = new TailscaleEcsNodeService(ecsManager, appConfig.getEcs(),
                                                                   appConfig.getAws());
    return new NodeServices(nodeService, ec2ClientPool);
  }

  TaskConfigService configureTaskConfigService(DynamoDbTable<RegionRecord> regions,
                                               RequestMetrics metrics) {
    TaskConfigService configService = new DynamoDbTaskConfigService(regions, metrics);
    if (!ConfigManager.isConfigCacheEnabled()) {
      return configService;
    }

    return new CachedTaskConfigService(configService, ConfigManager.getConfigCacheTtl(),
                                       Clock.systemUTC());
  }

}
