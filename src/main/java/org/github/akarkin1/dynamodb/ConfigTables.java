package org.github.akarkin1.dynamodb;

import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

/** The typed views of the config table; built once at init (schema creation uses reflection). */
public record ConfigTables(DynamoDbTable<RegionRecord> regions,
                           DynamoDbTable<UserRecord> users,
                           DynamoDbTable<PendingUserRecord> pendingUsers,
                           DynamoDbTable<TgUpdateLock> updateLocks,
                           DynamoDbTable<AccessRequestRecord> accessRequests) {

  public static ConfigTables create(DynamoDbEnhancedClient client, String tableName) {
    return new ConfigTables(
        client.table(tableName, TableSchema.fromBean(RegionRecord.class)),
        client.table(tableName, TableSchema.fromBean(UserRecord.class)),
        client.table(tableName, TableSchema.fromBean(PendingUserRecord.class)),
        client.table(tableName, TableSchema.fromBean(TgUpdateLock.class)),
        client.table(tableName, TableSchema.fromBean(AccessRequestRecord.class)));
  }

}
