package org.github.akarkin1.ui.screen;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.github.akarkin1.ui.screen.ScreenTestSupport.LINKS;
import static org.github.akarkin1.ui.screen.ScreenTestSupport.MENU;
import static org.github.akarkin1.ui.screen.ScreenTestSupport.assertPlaceholdersMatchParams;
import static org.junit.jupiter.api.Assertions.assertEquals;

class HelpScreenTest {

  private final HelpScreen helpScreen = new HelpScreen();

  @Test
  @DisplayName("AC-8: help shows title and body, links and menu buttons")
  void help() {
    Screen screen = helpScreen.help();

    assertEquals("❓ <b>${ui.help.title}</b>\n\n${ui.help.body}", screen.template());
    assertEquals(List.of(), screen.params());
    assertEquals(List.of(LINKS, List.of(MENU)), screen.keyboard());
  }

  @Test
  @DisplayName("AC-7: help template placeholders match params")
  void placeholdersMatchParams() {
    assertPlaceholdersMatchParams(helpScreen.help());
  }

}
