package org.github.akarkin1.auth;

import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.dynamodb.PendingUserRecord;
import org.github.akarkin1.dynamodb.UserRecord;
import org.github.akarkin1.metrics.RequestMetrics;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

import java.time.Clock;

public class PermissionsServiceConfigurer {

  // Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
  @SuppressWarnings("removal")
  public PermissionsService configure(DynamoDbTable<UserRecord> users,
                                      DynamoDbTable<PendingUserRecord> pendingUsers,
                                      RequestMetrics metrics) {
    PermissionsService permissionsService = new DynamoDbPermissionsService(users, pendingUsers,
                                                                           metrics);
    if (!ConfigManager.isConfigCacheEnabled()) {
      return permissionsService;
    }

    return new CachingPermissionsService(permissionsService, ConfigManager.getConfigCacheTtl(),
                                         Clock.systemUTC());
  }

}
