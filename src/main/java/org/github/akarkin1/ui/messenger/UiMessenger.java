package org.github.akarkin1.ui.messenger;

import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.Screen;

public interface UiMessenger {

  Integer send(UiContext context, Screen screen);

  void edit(UiContext context, Integer messageId, Screen screen);

  void answerCallback(String callbackQueryId);

}
