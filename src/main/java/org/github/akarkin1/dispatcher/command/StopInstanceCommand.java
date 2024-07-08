package org.github.akarkin1.dispatcher.command;


import lombok.RequiredArgsConstructor;
import org.github.akarkin1.exception.InvalidCommandException;
import org.github.akarkin1.ec2.SimpleEc2ClientProvider;

import java.util.List;

@RequiredArgsConstructor
public final class StopInstanceCommand implements BotCommand<TextCommandResponse> {

  private final SimpleEc2ClientProvider clientProvider;

  @Override
  public TextCommandResponse run(List<String> args) {
    if (args.isEmpty()) {
      throw new InvalidCommandException("Expected one argument: <serverName>, but no argument is provided");
    }

    String serverName = args.get(0);

    return null;
  }

  @Override
  public String getDescription() {
    return "stops server instance with provided InstanceId";
  }
}
