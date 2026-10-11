package org.github.akarkin1.ui;

import org.github.akarkin1.auth.TgUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.github.akarkin1.ui.TelegramUpdates.CHAT_ID;
import static org.github.akarkin1.ui.TelegramUpdates.USER_ID;
import static org.github.akarkin1.ui.TelegramUpdates.callbackUpdate;
import static org.github.akarkin1.ui.TelegramUpdates.messageUpdate;
import static org.github.akarkin1.ui.TelegramUpdates.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UiContextTest {

  @Test
  @DisplayName("AC-12, 4 AC-1: context of a message comes from its chat and sender, with the sender's user id")
  void fromMessage() {
    UiContext context = UiContext.fromUpdate(messageUpdate(user("alex", "Alex", "ru"), "hi"));

    assertEquals(new UiContext(CHAT_ID, USER_ID, "alex", "Alex", "ru"), context);
  }

  @Test
  @DisplayName("AC-12, 4 AC-1: context of a callback query comes from the tapping user (id included) and the message chat")
  void fromCallbackQuery() {
    UiContext context = UiContext.fromUpdate(callbackUpdate(user("alex", "Alex", "ru"), "HOME", true));

    assertEquals(new UiContext(CHAT_ID, USER_ID, "alex", "Alex", "ru"), context);
  }

  @Test
  @DisplayName("4 AC-1: a callback query without its message uses the user id as chat id and carries the user id")
  void fromCallbackQueryWithoutMessage() {
    UiContext context = UiContext.fromUpdate(callbackUpdate(user("alex", "Alex", "ru"), "HOME", false));

    assertEquals(new UiContext(USER_ID, USER_ID, "alex", "Alex", "ru"), context);
  }

  @Test
  @DisplayName("4 AC-1: user() is the TgUser of the request (user id and username)")
  void userOfRequest() {
    assertEquals(new TgUser(USER_ID, "alex"),
                 UiContext.fromUpdate(messageUpdate(user("alex", "Alex", "ru"), "hi")).user());
    assertEquals(new TgUser(USER_ID, "alex"),
                 UiContext.fromUpdate(callbackUpdate(user("alex", "Alex", "ru"), "HOME", true))
                     .user());
    assertEquals(new TgUser(42L, "bob"), new UiContext(100L, 42L, "bob", "Bob", "en").user());
  }

  @Test
  @DisplayName("4 AC-1: a user without a username still carries the user id; user() is not pending")
  void userWithoutUsername() {
    UiContext context = UiContext.fromUpdate(messageUpdate(user(null, "Anna", "ru"), "hi"));

    assertEquals(USER_ID, context.userId());
    assertEquals(new TgUser(USER_ID, null), context.user());
    assertFalse(context.user().pending());
  }

  @Test
  @DisplayName("AC-12: blank or missing language code defaults to en-US")
  void defaultLanguageCode() {
    assertEquals("en-US", UiContext.fromUpdate(messageUpdate(user("alex", "Alex", " "), "hi"))
        .languageCode());
    assertEquals("en-US", UiContext.fromUpdate(callbackUpdate(user("alex", "Alex", null), "HOME", true))
        .languageCode());
  }

}
