package org.github.akarkin1.tg;

import org.apache.commons.lang3.StringUtils;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.User;

import java.util.Optional;


public class TgRequestContext {

  private static final String DEFAULT_LANGUAGE_CODE = "en-US";
  private static String username;
  private static Long chatId;
  private static String languageCode;

  public static String getUsername() {
    return username;
  }

  public static Long getChatId() {
    return chatId;
  }

  public static String getLanguageCode() {
    return languageCode;
  }

  public static void initContext(Update update) {
    User fromUser = update.getMessage().getFrom();
    username = fromUser.getUserName();
    languageCode = Optional.ofNullable(fromUser.getLanguageCode())
        .filter(StringUtils::isNotBlank)
        .orElse(DEFAULT_LANGUAGE_CODE);
    chatId = update.getMessage().getChatId();
  }
}
