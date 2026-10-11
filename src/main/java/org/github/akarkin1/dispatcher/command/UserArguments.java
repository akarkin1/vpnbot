package org.github.akarkin1.dispatcher.command;

import org.github.akarkin1.auth.TgUser;

import java.util.Optional;

/**
 * Parses the user arguments of the admin text commands: a Telegram user id (all digits; a
 * username never is) or a username with or without {@code @}.
 */
final class UserArguments {

  private UserArguments() {
  }

  /** The user id when the (already normalized) argument is all digits. */
  static Optional<Long> parseId(String argument) {
    if (argument.isEmpty() || !argument.chars().allMatch(Character::isDigit)) {
      return Optional.empty();
    }
    try {
      return Optional.of(Long.valueOf(argument));
    } catch (NumberFormatException e) {
      return Optional.empty();
    }
  }

  /** {@code @username (id)}, {@code @username (${ui.users.pending})} or the id alone. */
  static String describe(TgUser user) {
    if (user.username() == null) {
      return String.valueOf(user.id());
    }
    return "@%s (%s)".formatted(user.username(), user.pending() ? "${ui.users.pending}" : user.id());
  }

}
