package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.ec2.Ec2ClientProvider;
import org.github.akarkin1.ec2.Ec2Manager;
import org.github.akarkin1.exception.InvalidCommandException;

import java.util.List;
import java.util.function.Consumer;

@RequiredArgsConstructor
public final class StopServerCommandV2 implements BotCommand<EmptyResponse> {

  private final Ec2ClientProvider clientProvider;
  private final Consumer<String> messageConsumer;


  @Override
  public EmptyResponse run(List<String> args) {
    if (args.isEmpty()) {
      throw new InvalidCommandException("Expected one argument: <ServerName>, but no argument is provided");
    }
    String serverName = args.get(0);
    Ec2Manager instanceManager = new Ec2Manager(clientProvider);
    instanceManager.stopServerGracefully(serverName, messageConsumer);

    return null;
  }

  @Override
  public String getDescription() {
    return "stops server by given ServerName";
  }
}
