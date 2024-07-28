package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.ec2.Ec2ClientProvider;
import org.github.akarkin1.ec2.Ec2Manager;
import org.github.akarkin1.exception.InvalidCommandException;

import java.util.List;

@RequiredArgsConstructor
public final class StartInstanceCommand implements BotCommand<TextCommandResponse> {

  private final Ec2ClientProvider clientProvider;

  @Override
  public TextCommandResponse run(List<String> args) {
    if (args.isEmpty()) {
      throw new InvalidCommandException("Expected one argument: <instanceId>, but no argument is provided");
    }
    String instanceId = args.get(0);
    Ec2Manager instanceManager = new Ec2Manager(clientProvider);
    instanceManager.startInstance(instanceId);

    return new TextCommandResponse("Start of the server with InstanceId: %s is initiated".formatted(instanceId));
  }

  @Override
  public String getDescription() {
    return "starts server instance with provided Instance Id";
  }
}
