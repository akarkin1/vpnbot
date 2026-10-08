package org.github.akarkin1.ui.screen;

import org.github.akarkin1.ui.UiAction;

import java.util.List;

final class CommonButtons {

  static final Button MENU = Button.action("🏠 ${ui.button.menu}", UiAction.home());
  static final List<Button> LINKS = List.of(
      Button.link("📖 ${ui.button.exit-node-guide}", Links.EXIT_NODE_GUIDE),
      Button.link("⬇️ ${ui.button.get-tailscale}", Links.DOWNLOAD));

  private CommonButtons() {
  }

}
