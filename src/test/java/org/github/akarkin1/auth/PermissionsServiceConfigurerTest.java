package org.github.akarkin1.auth;

import org.github.akarkin1.dynamodb.UserRecord;
import org.github.akarkin1.metrics.RecordingRequestMetrics;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.verifyNoInteractions;

/** Only the flag-off case: CONFIG_CACHE_ENABLED cannot be set from a test. */
@ExtendWith(MockitoExtension.class)
class PermissionsServiceConfigurerTest {

  @Mock
  private DynamoDbTable<UserRecord> users;

  @Test
  @DisplayName("3 AC-A12: with the cache flag off the DynamoDB service is returned unwrapped")
  void unwrappedWhenCacheDisabled() {
    assumeTrue(System.getenv("CONFIG_CACHE_ENABLED") == null, "CONFIG_CACHE_ENABLED is set");

    PermissionsService service = new PermissionsServiceConfigurer()
        .configure(users, new RecordingRequestMetrics());

    assertInstanceOf(DynamoDbPermissionsService.class, service);
    verifyNoInteractions(users);
  }

}
