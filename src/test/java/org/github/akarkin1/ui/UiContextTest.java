package org.github.akarkin1.ui;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.github.akarkin1.ui.TelegramUpdates.CHAT_ID;
import static org.github.akarkin1.ui.TelegramUpdates.callbackUpdate;
import static org.github.akarkin1.ui.TelegramUpdates.messageUpdate;
import static org.github.akarkin1.ui.TelegramUpdates.user;
import static org.junit.jupiter.api.Assertions.assertEquals;

class UiContextTest {

  @Test
  @DisplayName("AC-12: context of a message comes from its chat and sender")
  void fromMessage() {
    UiContext context = UiContext.fromUpdate(messageUpdate(user("alex", "Alex", "ru"), "hi"));

    assertEquals(new UiContext(CHAT_ID, "alex", "Alex", "ru"), context);
  }

  @Test
  @DisplayName("AC-12: context of a callback query comes from the tapping user and the message chat")
  void fromCallbackQuery() {
    UiContext context = UiContext.fromUpdate(callbackUpdate(user("alex", "Alex", "ru"), "HOME", true));

    assertEquals(new UiContext(CHAT_ID, "alex", "Alex", "ru"), context);
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
