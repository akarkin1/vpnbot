package org.github.akarkin1.ui;

import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.auth.TgUser;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

/** The user and chat of a request; {@code username} and {@code lastName} are nullable. */
public record UiContext(Long chatId, Long userId, String username, String firstName,
                        String lastName, String languageCode) {

  private static final String DEFAULT_LANGUAGE_CODE = "en-US";

  public static UiContext fromUpdate(Update update) {
    if (update.hasCallbackQuery()) {
      CallbackQuery callbackQuery = update.getCallbackQuery();
      User from = callbackQuery.getFrom();
      Long chatId = callbackQuery.getMessage() == null ? from.getId() : callbackQuery.getMessage().getChatId();
      return of(chatId, from);
    }
    if (update.hasMessage()) {
      return of(update.getMessage().getChatId(), update.getMessage().getFrom());
    }
    throw new IllegalArgumentException("Update has neither a message nor a callback query");
  }

  private static UiContext of(Long chatId, User user) {
    String languageCode = StringUtils.defaultIfBlank(user.getLanguageCode(), DEFAULT_LANGUAGE_CODE);
    return new UiContext(chatId, user.getId(), user.getUserName(), user.getFirstName(),
                         user.getLastName(), languageCode);
  }

  /** The user of the request. */
  public TgUser user() {
    return new TgUser(userId, username);
  }

}
