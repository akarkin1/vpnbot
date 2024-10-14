package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.ec2.Ec2Manager;
import org.github.akarkin1.exception.InvalidCommandException;

import java.util.List;

@RequiredArgsConstructor
public final class RebootServerCommand implements BotCommand<TextCommandResponse> {

  private final Ec2Manager instanceManager;

  @Override
  public TextCommandResponse run(List<String> args) {
    if (args.isEmpty()) {
      throw new InvalidCommandException("Expected one argument: <ServerName>, but no argument is provided");
    }
    String serverName = args.get(0);
    instanceManager.rebootServer(serverName);

    return new TextCommandResponse("Rebooting the server with ServerName: %s ...".formatted(serverName));
  }

  @Override
  public String getDescription() {
    return "reboot server with provided ServerName; reboot doesn't reassign IP to the instance";
  }
}
