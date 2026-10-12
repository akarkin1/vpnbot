package org.github.akarkin1.dynamodb;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;

// Pending users are deprecated: pre-registration by username ended with 2026.10. Remove once
// PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
@SuppressWarnings("removal")
@ExtendWith(MockitoExtension.class)
class ConfigTablesTest {

  @Mock
  private DynamoDbClient dynamoDbClient;

  @Test
  @DisplayName("3 AC-A13, 4 §5, 5 §5: the typed views (incl. pending users, access requests, region requests) are views of the one named table")
  void viewsOfOneTable() {
    DynamoDbEnhancedClient client = DynamoDbEnhancedClient.builder()
        .dynamoDbClient(dynamoDbClient)
        .build();

    ConfigTables tables = ConfigTables.create(client, "vpnbot");

    assertEquals("vpnbot", tables.regions().tableName());
    assertEquals("vpnbot", tables.users().tableName());
    assertEquals("vpnbot", tables.pendingUsers().tableName());
    assertEquals("vpnbot", tables.updateLocks().tableName());
    assertEquals("vpnbot", tables.accessRequests().tableName());
    assertEquals("vpnbot", tables.regionRequests().tableName());
    assertEquals(RegionRecord.class, tables.regions().tableSchema().itemType().rawClass());
    assertEquals(UserRecord.class, tables.users().tableSchema().itemType().rawClass());
    assertEquals(PendingUserRecord.class, tables.pendingUsers().tableSchema().itemType().rawClass());
    assertEquals(TgUpdateLock.class, tables.updateLocks().tableSchema().itemType().rawClass());
    assertEquals(AccessRequestRecord.class,
                 tables.accessRequests().tableSchema().itemType().rawClass());
    assertEquals(RegionRequestRecord.class,
                 tables.regionRequests().tableSchema().itemType().rawClass());
    verifyNoInteractions(dynamoDbClient);
  }

}
