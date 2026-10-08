package org.github.akarkin1.ui.screen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.github.akarkin1.ui.screen.ScreenTestSupport.MENU;
import static org.github.akarkin1.ui.screen.ScreenTestSupport.assertPlaceholdersMatchParams;
import static org.junit.jupiter.api.Assertions.assertEquals;

class ErrorScreenTest {

  private final ErrorScreen errorScreen = new ErrorScreen();

  @Test
  @DisplayName("AC-8: generic error shows the error text and a menu button")
  void generic() {
    Screen screen = errorScreen.generic();

    assertEquals("⚠️ ${ui.error.generic}", screen.template());
    assertEquals(List.of(), screen.params());
    assertEquals(List.of(List.of(MENU)), screen.keyboard());
  }

  @Test
  @DisplayName("AC-7: error template placeholders match params")
  void placeholdersMatchParams() {
    assertPlaceholdersMatchParams(errorScreen.generic());
  }

}
