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

@ExtendWith(MockitoExtension.class)
class ConfigTablesTest {

  @Mock
  private DynamoDbClient dynamoDbClient;

  @Test
  @DisplayName("3 AC-A13: the three typed views are views of the one named table")
  void viewsOfOneTable() {
    DynamoDbEnhancedClient client = DynamoDbEnhancedClient.builder()
        .dynamoDbClient(dynamoDbClient)
        .build();

    ConfigTables tables = ConfigTables.create(client, "vpnbot");

    assertEquals("vpnbot", tables.regions().tableName());
    assertEquals("vpnbot", tables.users().tableName());
    assertEquals("vpnbot", tables.updateLocks().tableName());
    assertEquals(RegionRecord.class, tables.regions().tableSchema().itemType().rawClass());
    assertEquals(UserRecord.class, tables.users().tableSchema().itemType().rawClass());
    assertEquals(TgUpdateLock.class, tables.updateLocks().tableSchema().itemType().rawClass());
    verifyNoInteractions(dynamoDbClient);
  }

}
