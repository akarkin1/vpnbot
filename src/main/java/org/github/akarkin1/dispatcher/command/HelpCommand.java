package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.dispatcher.CommandDispatcher;

import java.util.List;

@Log4j2
@RequiredArgsConstructor
public final class HelpCommand implements BotCommand<TextCommandResponse> {
  private static final String APP_DESCRIPTION = "The bot allows to control EC2 Instances (Servers) "
      + "deployed in AWS in multiple regions. Currently only 3 regions are supported: "
      + "us-east-1 (North Virginia), eu-north-1 (Stockholm), eu-west-2 (London)."
      + "For more details please contact the developer: @karkin_ai . \n"
      + "The commands supported: %n%s";

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
