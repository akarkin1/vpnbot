package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;
import org.github.akarkin1.service.NodeService;
import org.github.akarkin1.service.ServiceType;

import java.util.List;

/**
 * Command to list supported service types.
 */
@RequiredArgsConstructor
public final class ListServicesCommand implements BotCommand<TextCommandResponse> {

  private final NodeService nodeService;

  @Override
  public TextCommandResponse run(List<String> args) {
    List<ServiceType> serviceTypes = nodeService.getSupportedServiceTypes();

    if (serviceTypes.isEmpty()) {
      return new TextCommandResponse("${command.list-services.no-services.message}");
    }

    StringBuilder responseBuilder = new StringBuilder();
    responseBuilder.append("${command.list-services.supported-services.message}: ").append("\n");

    serviceTypes.forEach(serviceType ->
                          responseBuilder.append("\t– %s".formatted(serviceType.getDisplayName()))
                              .append("\n"));

    return new TextCommandResponse(responseBuilder.toString());
  }

  @Override
  public String getDescription() {
    return "${command.list-services.description.message}";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.LIST_NODES);
  }
}