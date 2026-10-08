package org.github.akarkin1.ui.screen;

import org.github.akarkin1.ui.UiAction;

import java.util.List;

public class ErrorScreen {

  private static final Button MENU = Button.action("🏠 ${ui.button.menu}", UiAction.home());

  public Screen generic() {
    return new Screen("⚠️ ${ui.error.generic}", List.of(), List.of(List.of(MENU)));
  }

}
