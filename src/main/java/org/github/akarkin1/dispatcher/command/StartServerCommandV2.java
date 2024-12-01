package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.ec2.Ec2Manager;
import org.github.akarkin1.exception.InvalidCommandException;
import org.github.akarkin1.message.MessageConsumer;

import java.util.List;

@RequiredArgsConstructor
public final class StartServerCommandV2 implements BotCommand<EmptyResponse> {

  private final Ec2Manager instanceManager;
  private final MessageConsumer messageConsumer;

  @Override
  public EmptyResponse run(List<String> args) {
    if (args.isEmpty()) {
      throw new InvalidCommandException("Expected one argument: <ServerName>, but no argument is provided");
    }
    String serverName = args.get(0);
    instanceManager.startServerGracefully(serverName, messageConsumer);

    return EmptyResponse.NONE;
  }

  @Override
  public String getDescription() {
    return "starts server by given ServerName";
  }
}
