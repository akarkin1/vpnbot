package org.github.akarkin1.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/** The typed views of the config table; built once at init (schema creation uses reflection). */
public record ConfigTables(DynamoDbTable<RegionRecord> regions,
                           DynamoDbTable<UserRecord> users,
                           // Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
                           @SuppressWarnings("removal") DynamoDbTable<PendingUserRecord> pendingUsers,
                           DynamoDbTable<TgUpdateLock> updateLocks,
                           DynamoDbTable<AccessRequestRecord> accessRequests) {

  // Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
  @SuppressWarnings("removal")
  public static ConfigTables create(DynamoDbEnhancedClient client, String tableName) {
    return new ConfigTables(
        client.table(tableName, TableSchema.fromBean(RegionRecord.class)),
        client.table(tableName, TableSchema.fromBean(UserRecord.class)),
        client.table(tableName, TableSchema.fromBean(PendingUserRecord.class)),
        client.table(tableName, TableSchema.fromBean(TgUpdateLock.class)),
        client.table(tableName, TableSchema.fromBean(AccessRequestRecord.class)));
  }

  /**
   * The {@code PENDING_USER} records.
   *
   * @deprecated Pre-registration by username ended with 2026.10. Remove once
   *     {@code PENDING_USER} records no longer exist (query {@code pk = PENDING_USER}); see
   *     docs/specs/cleanup-2026-10.md §4.3.
   */
  @Deprecated(forRemoval = true)
  // Pre-registration by username ended with 2026.10. Remove once PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
  @SuppressWarnings("removal")
  @Override
  public DynamoDbTable<PendingUserRecord> pendingUsers() {
    return pendingUsers;
  }

}
