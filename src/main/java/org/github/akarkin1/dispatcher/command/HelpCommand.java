package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.CommandDispatcher;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;

import java.util.Collections;
import java.util.List;

@Log4j2
@RequiredArgsConstructor
public final class HelpCommand implements BotCommand<TextCommandResponse> {

  private final CommandDispatcher commandDispatcher;

  @Override
  public TextCommandResponse run(List<String> args) {
    return new TextCommandResponse("${command.help.app-description.message}%n" + commandDispatcher.getSupportedCommands());
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
