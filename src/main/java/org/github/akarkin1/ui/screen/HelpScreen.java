package org.github.akarkin1.ui.screen;

import org.github.akarkin1.ui.UiAction;

import java.util.List;

public class HelpScreen {

  private static final Button MENU = Button.action("🏠 ${ui.button.menu}", UiAction.home());
  private static final List<Button> LINKS = List.of(
      Button.link("📖 ${ui.button.exit-node-guide}", Links.EXIT_NODE_GUIDE),
      Button.link("⬇️ ${ui.button.get-tailscale}", Links.DOWNLOAD));

  public Screen help() {
    return new Screen("❓ <b>${ui.help.title}</b>\n\n${ui.help.body}", List.of(), List.of(LINKS, List.of(MENU)));
  }

}
