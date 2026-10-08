package org.github.akarkin1.ui;

import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

public record UiContext(Long chatId, String username, String firstName, String languageCode) {

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
    return new UiContext(chatId, user.getUserName(), user.getFirstName(), languageCode);
  }

}
