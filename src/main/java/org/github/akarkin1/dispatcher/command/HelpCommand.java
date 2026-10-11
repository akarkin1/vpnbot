package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.CommandCatalog;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.Screen;

import java.util.Collections;
import java.util.List;

@Log4j2
@RequiredArgsConstructor
public final class HelpCommand implements BotCommand {

  private final CommandCatalog catalog;

  @Override
  public Screen run(UiContext context, List<String> args) {
    return BotCommand.reply("${command.help.app-description.message}%n"
                            + catalog.supportedCommands(context.user()));
  }

  @Override
  public String getDescription() {
    return "${command.help.description.message}";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return Collections.emptyList();
  }

}
