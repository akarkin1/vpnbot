package org.github.akarkin1.auth;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public interface PermissionsService extends UserSignupService {

  /**
   * Every known user with their permissions: {@code USER} records (id + username) and
   * {@code PENDING_USER} records (username only, {@link TgUser#pending()}).
   */
  Map<TgUser, List<Permission>> getUsers();

  /**
   * Finds a known user by username, ignoring case and a leading {@code @}. A user with an id
   * ({@code USER} record) wins over a pending one with the same username.
   */
  default Optional<TgUser> findByUsername(String username) {
    if (username == null) {
      return Optional.empty();
    }
    String wanted = (username.startsWith("@") ? username.substring(1) : username)
        .toLowerCase(Locale.ROOT);
    if (wanted.isEmpty()) {
      return Optional.empty();
    }

    Optional<TgUser> pendingMatch = Optional.empty();
    for (TgUser user : getUsers().keySet()) {
      if (user.username() == null || !wanted.equals(user.username().toLowerCase(Locale.ROOT))) {
        continue;
      }
      if (!user.pending()) {
        return Optional.of(user);
      }
      pendingMatch = Optional.of(user);
    }
    return pendingMatch;
  }

}
