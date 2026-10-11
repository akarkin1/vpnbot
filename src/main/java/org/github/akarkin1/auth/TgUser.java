package org.github.akarkin1.auth;

import java.util.Locale;
import java.util.Objects;

/**
 * A Telegram user: the immutable user id and the (changeable) username. Requests always carry the
 * id; a user pre-registered by an admin by username only has no id yet ({@link #pending()}).
 *
 * <p>Identity: two users with an id are equal when their ids are equal, whatever their usernames
 * (a renamed user stays the same user), so a {@code TgUser} built from a request finds the stored
 * entry in a {@code Map<TgUser, ...>}. Two users without an id are equal when their usernames are
 * equal ignoring case. A user with an id never equals one without.
 */
public record TgUser(Long id, String username) {

  /** The id as a decimal string when known, else {@code @username}; used in callback data. */
  public String key() {
    return id != null ? String.valueOf(id) : "@" + username;
  }

  /**
   * True for a user known by username only (a {@code PENDING_USER} record).
   *
   * @deprecated Pre-registration by username ended with 2026.10. Remove once
   *     {@code PENDING_USER} records no longer exist (query {@code pk = PENDING_USER}); see
   *     docs/specs/cleanup-2026-10.md §4.3.
   */
  @Deprecated(forRemoval = true)
  public boolean pending() {
    return id == null;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (!(o instanceof TgUser other)) {
      return false;
    }
    if (id != null || other.id != null) {
      return Objects.equals(id, other.id);
    }
    return Objects.equals(normalized(username), normalized(other.username));
  }

  @Override
  public int hashCode() {
    return id != null ? Long.hashCode(id) : Objects.hashCode(normalized(username));
  }

  private static String normalized(String username) {
    return username == null ? null : username.toLowerCase(Locale.ROOT);
  }

}
