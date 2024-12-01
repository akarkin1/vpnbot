package org.github.akarkin1.dispatcher.command.ecs;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.command.TextCommandResponse;

import java.util.Collections;
import java.util.List;

@Log4j2
@RequiredArgsConstructor
public final class HelpCommandV2 implements BotCommandV2<TextCommandResponse> {

  private final CommandDispatcherV2 commandDispatcher;

  @Override
  public TextCommandResponse run(List<String> args) {
    return new TextCommandResponse("${command.help.app-description.message}",
                                   commandDispatcher.getSupportedCommands());
  }

  @Override
  public String getDescription() {
    return "prints help message";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return Collections.emptyList();
  }

}
