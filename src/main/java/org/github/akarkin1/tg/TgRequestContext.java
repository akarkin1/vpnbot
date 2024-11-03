package org.github.akarkin1.tg;

import lombok.Getter;
import org.telegram.telegrambots.meta.api.objects.Update;


public class TgRequestContext {
  @Getter
  private static String username;
  @Getter
  private static Long chatId;

  public static void initContext(Update update) {
    username = update.getMessage().getFrom().getUserName();
    chatId = update.getMessage().getChatId();
  }
}
