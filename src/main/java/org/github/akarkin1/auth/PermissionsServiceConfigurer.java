package org.github.akarkin1.auth;

import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.dynamodb.UserRecord;
import org.github.akarkin1.metrics.RequestMetrics;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

import java.time.Clock;

public class PermissionsServiceConfigurer {

  public PermissionsService configure(DynamoDbTable<UserRecord> users, RequestMetrics metrics) {
    PermissionsService permissionsService = new DynamoDbPermissionsService(users, metrics);
    if (!ConfigManager.isConfigCacheEnabled()) {
      return permissionsService;
    }

    return new CachingPermissionsService(permissionsService, ConfigManager.getConfigCacheTtl(),
                                         Clock.systemUTC());
  }

}
