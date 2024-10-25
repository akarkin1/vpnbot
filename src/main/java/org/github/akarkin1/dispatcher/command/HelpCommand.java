package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.dispatcher.CommandDispatcher;

import java.util.List;

@Log4j2
@RequiredArgsConstructor
public final class HelpCommand implements BotCommand<TextCommandResponse> {
  private static final String APP_DESCRIPTION = "The bot allows to manage VPN Servers deployed in "
                                                + "AWS.%nThe list of supported commands:%n %s";

  private final CommandDispatcher commandDispatcher;

  @Override
  public TextCommandResponse run(List<String> args) {
    String responseContent = APP_DESCRIPTION.formatted(commandDispatcher.getSupportedCommands());
    return new TextCommandResponse(responseContent);
  }

  @Override
  public String getDescription() {
    return "prints help message";
  }

}
