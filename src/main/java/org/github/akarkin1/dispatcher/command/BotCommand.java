package org.github.akarkin1.dispatcher.command;

import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.CommonButtons;
import org.github.akarkin1.ui.screen.Screen;

import java.util.List;

public interface BotCommand {

  Screen run(UiContext context, List<String> args);

  String getDescription();

  List<Permission> getRequiredPermissions();

  /** A system-command reply: the text with its params and the Menu row. */
  static Screen reply(String template, Object... params) {
    return new Screen(template, List.of(params), List.of(List.of(CommonButtons.MENU)));
  }

}
