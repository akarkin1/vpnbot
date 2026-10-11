package org.github.akarkin1.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pending users are deprecated: pre-registration by username ended with 2026.10. Remove once
// PENDING_USER records no longer exist (query pk = PENDING_USER); see docs/specs/cleanup-2026-10.md §4.3.
@SuppressWarnings("removal")
class TgUserTest {

  @Test
  @DisplayName("4 §4.1: the key of a user with an id is the id as a decimal string")
  void keyOfUserWithId() {
    assertEquals("123456789", new TgUser(123456789L, "alice").key());
    assertEquals("9999999999", new TgUser(9_999_999_999L, null).key());
  }

  @Test
  @DisplayName("4 §4.1: the key of a pre-registered user (no id) is @username")
  void keyOfPendingUser() {
    assertEquals("@bob", new TgUser(null, "bob").key());
  }

  @Test
  @DisplayName("4 §4.1: pending means no id")
  void pending() {
    assertTrue(new TgUser(null, "bob").pending());
    assertFalse(new TgUser(42L, "alice").pending());
    assertFalse(new TgUser(42L, null).pending());
  }

  @Test
  @DisplayName("4 §4.1: TgUser is a value: equal id and username make equal users")
  void valueEquality() {
    assertEquals(new TgUser(42L, "alice"), new TgUser(42L, "alice"));
    assertEquals(new TgUser(42L, "alice").hashCode(), new TgUser(42L, "alice").hashCode());
  }

}
