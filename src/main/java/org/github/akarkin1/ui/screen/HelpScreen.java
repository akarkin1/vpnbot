package org.github.akarkin1.ui.screen;

import java.util.List;

import static org.github.akarkin1.ui.screen.CommonButtons.LINKS;
import static org.github.akarkin1.ui.screen.CommonButtons.MENU;

public class HelpScreen {

  public Screen help() {
    return new Screen("❓ <b>${ui.help.title}</b>\n\n${ui.help.body}", List.of(), List.of(LINKS, List.of(MENU)));
  }

}
