package org.github.akarkin1.auth;

import org.github.akarkin1.dynamodb.PendingUserRecord;
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
// Pending users are deprecated: pre-registration by username ended with 2026.10. Remove once
// PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
@SuppressWarnings("removal")
@ExtendWith(MockitoExtension.class)
class PermissionsServiceConfigurerTest {

  @Mock
  private DynamoDbTable<UserRecord> users;
  @Mock
  private DynamoDbTable<PendingUserRecord> pendingUsers;

  @Test
  @DisplayName("3 AC-A12: with the cache flag off the DynamoDB service is returned unwrapped")
  void unwrappedWhenCacheDisabled() {
    assumeTrue(System.getenv("CONFIG_CACHE_ENABLED") == null, "CONFIG_CACHE_ENABLED is set");

    PermissionsService service = new PermissionsServiceConfigurer()
        .configure(users, pendingUsers, new RecordingRequestMetrics());

    assertInstanceOf(DynamoDbPermissionsService.class, service);
    verifyNoInteractions(users, pendingUsers);
  }

}
