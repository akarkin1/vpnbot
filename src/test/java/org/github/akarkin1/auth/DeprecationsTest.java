package org.github.akarkin1.auth;

import org.github.akarkin1.dynamodb.ConfigTables;
import org.github.akarkin1.dynamodb.PendingUserRecord;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.AnnotatedElement;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins cleanup-2026-10 D-10: the pending-user code is deprecated for removal, not removed, so that
 * its later removal (once the {@code PENDING_USER} query is empty) is a deliberate change.
 */
// Pending users are deprecated: pre-registration by username ended with 2026.10. Remove once
// PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
@SuppressWarnings("removal")
class DeprecationsTest {

  @Test
  @DisplayName("cleanup §4.3, D-10: PendingUserRecord is @Deprecated(forRemoval = true)")
  void pendingUserRecord() {
    assertDeprecatedForRemoval(PendingUserRecord.class);
  }

  @Test
  @DisplayName("cleanup §4.3, D-10: PendingUserActivator is @Deprecated(forRemoval = true)")
  void pendingUserActivator() {
    assertDeprecatedForRemoval(PendingUserActivator.class);
  }

  @Test
  @DisplayName("cleanup §4.3, D-10: TgUser.pending() is @Deprecated(forRemoval = true)")
  void tgUserPending() throws NoSuchMethodException {
    assertDeprecatedForRemoval(TgUser.class.getMethod("pending"));
  }

  @Test
  @DisplayName("cleanup §4.3, D-10: ConfigTables.pendingUsers() is @Deprecated(forRemoval = true)")
  void configTablesPendingUsers() throws NoSuchMethodException {
    assertDeprecatedForRemoval(ConfigTables.class.getMethod("pendingUsers"));
  }

  private static void assertDeprecatedForRemoval(AnnotatedElement element) {
    Deprecated deprecated = element.getAnnotation(Deprecated.class);
    assertNotNull(deprecated, element + " is not @Deprecated");
    assertTrue(deprecated.forRemoval(), element + " is @Deprecated but not forRemoval = true");
  }

}
