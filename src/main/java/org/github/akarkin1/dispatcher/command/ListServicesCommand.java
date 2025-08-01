package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;
import org.github.akarkin1.service.NodeService;

import java.util.List;

/**
 * Command to list supported service types.
 */
@RequiredArgsConstructor
public final class ListServicesCommand implements BotCommand<TextCommandResponse> {

  private final NodeService nodeService;

  @Override
  public TextCommandResponse run(List<java.lang.String> args) {
    List<String> strings = nodeService.getSupportedServiceTypes();

    if (strings.isEmpty()) {
      return new TextCommandResponse("${command.list-services.no-services.message}");
    }

    StringBuilder responseBuilder = new StringBuilder();
    responseBuilder.append("${command.list-services.supported-services.message}: ").append("\n");

    strings.forEach(serviceType ->
                          responseBuilder.append("\t– %s".formatted(serviceType))
                              .append("\n"));

    return new TextCommandResponse(responseBuilder.toString());
  }

  @Override
  public java.lang.String getDescription() {
    return "${command.list-services.description.message}";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.LIST_NODES);
  }
}