package org.github.akarkin1.dispatcher.command;


import lombok.RequiredArgsConstructor;
import org.github.akarkin1.ec2.Ec2ClientProvider;
import org.github.akarkin1.ec2.Ec2InstanceManager;
import org.github.akarkin1.exception.InvalidCommandException;

import java.util.List;

@RequiredArgsConstructor
public final class StopServerCommand implements BotCommand<TextCommandResponse> {

  private final Ec2ClientProvider clientProvider;

  @Override
  public TextCommandResponse run(List<String> args) {
    if (args.isEmpty()) {
      throw new InvalidCommandException("Expected one argument: <ServerName>, but no argument is provided");
    }
    String serverName = args.get(0);
    Ec2InstanceManager instanceManager = new Ec2InstanceManager(clientProvider);
    instanceManager.stopServer(serverName);

    return new TextCommandResponse("Stop of server with ServerName: %s is initiated".formatted(serverName));
  }

  @Override
  public String getDescription() {
    return "stops server instance with provided ServerName";
  }
}
