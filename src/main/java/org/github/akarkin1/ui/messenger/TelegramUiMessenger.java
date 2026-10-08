package org.github.akarkin1.ui.messenger;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.translation.Translator;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.Screen;
import org.telegram.telegrambots.meta.bots.AbsSender;

@Log4j2
@RequiredArgsConstructor
public class TelegramUiMessenger implements UiMessenger {

  private final AbsSender sender;
  private final Translator translator;

  @Override
  public void send(UiContext context, Screen screen) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void edit(UiContext context, Integer messageId, Screen screen) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  @Override
  public void answerCallback(String callbackQueryId) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}
