package org.github.akarkin1.ui;

import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Chat;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

/** Builders for Telegram updates used by the UI tests. */
public final class TelegramUpdates {

  public static final long USER_ID = 7L;
  public static final long CHAT_ID = 100L;
  public static final int MESSAGE_ID = 42;
  public static final String CALLBACK_ID = "cb-1";

  private TelegramUpdates() {
  }

  public static User user(String username, String firstName, String languageCode) {
    User user = new User();
    user.setId(USER_ID);
    user.setUserName(username);
    user.setFirstName(firstName);
    user.setLanguageCode(languageCode);
    return user;
  }

  public static Message message(User from, String text) {
    Chat chat = new Chat();
    chat.setId(CHAT_ID);
    chat.setType("private");
    Message message = new Message();
    message.setMessageId(MESSAGE_ID);
    message.setChat(chat);
    message.setFrom(from);
    message.setText(text);
    return message;
  }

  public static Update messageUpdate(String text) {
    return messageUpdate(user("alex", "Alex", "ru"), text);
  }

  public static Update messageUpdate(User from, String text) {
    Update update = new Update();
    update.setMessage(message(from, text));
    return update;
  }

  public static Update callbackUpdate(String data) {
    return callbackUpdate(user("alex", "Alex", "ru"), data, true);
  }

  /** A callback query from {@code from}; the bot's message it belongs to is present when {@code withMessage}. */
  public static Update callbackUpdate(User from, String data, boolean withMessage) {
    CallbackQuery callbackQuery = new CallbackQuery();
    callbackQuery.setId(CALLBACK_ID);
    callbackQuery.setFrom(from);
    callbackQuery.setData(data);
    if (withMessage) {
      callbackQuery.setMessage(message(botUser(), "home"));
    }
    Update update = new Update();
    update.setCallbackQuery(callbackQuery);
    return update;
  }

  private static User botUser() {
    User bot = new User();
    bot.setId(1L);
    bot.setUserName("vpn_bot");
    bot.setIsBot(true);
    return bot;
  }

}
