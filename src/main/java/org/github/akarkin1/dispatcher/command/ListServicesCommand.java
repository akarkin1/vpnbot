package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.config.ConfigManager;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;
import org.github.akarkin1.tg.TgRequestContext;

import java.util.List;
import java.util.Set;

/**
 * Command to list supported service types.
 */
@RequiredArgsConstructor
public final class ListServicesCommand implements BotCommand<TextCommandResponse> {

  private final Authorizer authorizer;

  @Override
  public TextCommandResponse run(List<java.lang.String> args) {
    String username = TgRequestContext.getUsername();
    if (username == null) {
      return new TextCommandResponse("${command.list-nodes.no-permission}");
    }

    Set<String> supportedServices = authorizer.getAllowedServices(username);

    if (supportedServices.isEmpty()) {
      return new TextCommandResponse("${command.list-services.no-services.message}");
    }

    StringBuilder responseBuilder = new StringBuilder();
    responseBuilder.append("${command.list-services.supported-services.message}: ").append("\n");

    supportedServices.forEach(serviceType ->
                          responseBuilder.append("\t– %s".formatted(serviceType))
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