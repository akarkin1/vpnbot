package org.github.akarkin1.dispatcher.command;


import lombok.RequiredArgsConstructor;
import org.github.akarkin1.ec2.Ec2Manager;
import org.github.akarkin1.exception.InvalidCommandException;

import java.util.List;

@RequiredArgsConstructor
public final class StopInstanceCommand implements BotCommand<TextCommandResponse> {

  private final Ec2Manager instanceManager;

  @Override
  public TextCommandResponse run(List<String> args) {
    if (args.isEmpty()) {
      throw new InvalidCommandException("Expected one argument: <instanceId>, but no argument is provided");
    }
    String instanceId = args.get(0);
    instanceManager.stopInstance(instanceId);

    return new TextCommandResponse("Stop of the server with InstanceId: %s is initiated".formatted(instanceId));
  }

  @Override
  public String getDescription() {
    return "stops server instance with provided InstanceId";
  }
}
