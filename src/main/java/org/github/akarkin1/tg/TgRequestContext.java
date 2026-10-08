package org.github.akarkin1.tg;

import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.objects.CallbackQuery;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Optional;


public class TgRequestContext {

  private static final String DEFAULT_LANGUAGE_CODE = "en-US";
  @Getter
  private static String username;
  @Getter
  private static Long chatId;
  @Getter
  private static String languageCode;

  public static void initContext(Update update) {
    if (update.hasMessage()) {
      initContext(update.getMessage().getFrom(), update.getMessage().getChatId());
    } else if (update.hasCallbackQuery()) {
      CallbackQuery callbackQuery = update.getCallbackQuery();
      User fromUser = callbackQuery.getFrom();
      Long callbackChatId = Optional.ofNullable(callbackQuery.getMessage())
          .map(Message::getChatId)
          .orElse(fromUser.getId());
      initContext(fromUser, callbackChatId);
    } else {
      username = null;
      languageCode = DEFAULT_LANGUAGE_CODE;
      chatId = null;
    }
  }

  private static void initContext(User fromUser, Long userChatId) {
    username = fromUser.getUserName();
    languageCode = Optional.ofNullable(fromUser.getLanguageCode())
        .filter(StringUtils::isNotBlank)
        .orElse(DEFAULT_LANGUAGE_CODE);
    chatId = userChatId;
  }
}
