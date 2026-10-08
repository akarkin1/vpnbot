package org.github.akarkin1.ui;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.ui.controller.HomeController;
import org.github.akarkin1.ui.controller.LaunchController;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.ErrorScreen;
import org.telegram.telegrambots.meta.api.objects.Update;

@Log4j2
@RequiredArgsConstructor
public class UiRouter {

  private final HomeController homeController;
  private final LaunchController launchController;
  private final UiMessenger messenger;
  private final ErrorScreen errorScreen;

  public boolean canHandle(Update update) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public void handle(Update update) {
    throw new UnsupportedOperationException("Not implemented yet");
  }

}
