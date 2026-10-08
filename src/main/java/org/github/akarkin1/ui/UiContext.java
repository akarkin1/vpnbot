package org.github.akarkin1.ui;

import org.telegram.telegrambots.meta.api.objects.Update;

public record UiContext(Long chatId, String username, String firstName, String languageCode) {

  public static UiContext fromUpdate(Update update) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}
