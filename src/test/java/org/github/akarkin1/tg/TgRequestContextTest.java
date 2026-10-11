package org.github.akarkin1.tg;

import org.github.akarkin1.auth.TgUser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.telegram.telegrambots.meta.api.objects.Update;

import static org.github.akarkin1.ui.TelegramUpdates.CHAT_ID;
import static org.github.akarkin1.ui.TelegramUpdates.USER_ID;
import static org.github.akarkin1.ui.TelegramUpdates.callbackUpdate;
import static org.github.akarkin1.ui.TelegramUpdates.messageUpdate;
import static org.github.akarkin1.ui.TelegramUpdates.user;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TgRequestContextTest {

  @Test
  @DisplayName("AC-14: callback query takes the user from the callback and the chat from its message")
  void callbackQuery() {
    TgRequestContext.initContext(callbackUpdate(user("alex", "Alex", "ru"), "HOME", true));

    assertEquals("alex", TgRequestContext.getUsername());
    assertEquals(CHAT_ID, TgRequestContext.getChatId());
    assertEquals("ru", TgRequestContext.getLanguageCode());
  }

  @Test
  @DisplayName("AC-14: callback query without a message uses the user id as chat id")
  void callbackQueryWithoutMessage() {
    TgRequestContext.initContext(callbackUpdate(user("alex", "Alex", "ru"), "HOME", false));

    assertEquals("alex", TgRequestContext.getUsername());
    assertEquals(USER_ID, TgRequestContext.getChatId());
  }

  @Test
  @DisplayName("AC-14: callback query with a blank language code uses the default")
  void callbackQueryDefaultLanguage() {
    TgRequestContext.initContext(callbackUpdate(user("alex", "Alex", " "), "HOME", true));

    assertEquals("en-US", TgRequestContext.getLanguageCode());
  }

  @Test
  @DisplayName("AC-14: message updates work as before")
  void message() {
    TgRequestContext.initContext(messageUpdate(user("bob", "Bob", "en"), "/help"));

    assertEquals("bob", TgRequestContext.getUsername());
    assertEquals(CHAT_ID, TgRequestContext.getChatId());
    assertEquals("en", TgRequestContext.getLanguageCode());
  }

  @Test
  @DisplayName("4 §4.1: getUser() is the TgUser of the sender of a message (user id and username)")
  void userOfMessage() {
    TgRequestContext.initContext(messageUpdate(user("bob", "Bob", "en"), "/help"));

    assertEquals(new TgUser(USER_ID, "bob"), TgRequestContext.getUser());
  }

  @Test
  @DisplayName("4 §4.1: getUser() is the TgUser of the tapping user of a callback query")
  void userOfCallbackQuery() {
    TgRequestContext.initContext(callbackUpdate(user("alex", "Alex", "ru"), "HOME", false));

    assertEquals(new TgUser(USER_ID, "alex"), TgRequestContext.getUser());
  }

  @Test
  @DisplayName("4 §4.1: a sender without a username still has a TgUser with the user id")
  void userWithoutUsername() {
    TgRequestContext.initContext(messageUpdate(user(null, "Anna", "en"), "/help"));

    assertEquals(new TgUser(USER_ID, null), TgRequestContext.getUser());
  }

  @Test
  @DisplayName("4 §4.1: after a reset there is no user id")
  void userAfterReset() {
    TgRequestContext.initContext(messageUpdate(user("bob", "Bob", "ru"), "/help"));

    TgRequestContext.initContext(new Update());

    TgUser user = TgRequestContext.getUser();
    assertTrue(user == null || user.id() == null, String.valueOf(user));
  }

  @Test
  @DisplayName("AC-14: any other update resets the context")
  void otherUpdateResetsContext() {
    TgRequestContext.initContext(messageUpdate(user("bob", "Bob", "ru"), "/help"));

    TgRequestContext.initContext(new Update());

    assertNull(TgRequestContext.getUsername());
    assertNull(TgRequestContext.getChatId());
    assertEquals("en-US", TgRequestContext.getLanguageCode());
  }

}
