package org.github.akarkin1.ui.screen;

import java.util.List;

import static org.github.akarkin1.ui.screen.CommonButtons.MENU;

public class ErrorScreen {

  public Screen generic() {
    return new Screen("⚠️ ${ui.error.generic}", List.of(), List.of(List.of(MENU)));
  }

}
