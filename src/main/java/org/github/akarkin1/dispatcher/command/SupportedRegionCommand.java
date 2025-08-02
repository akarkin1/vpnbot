package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;
import org.github.akarkin1.service.NodeService;
import org.github.akarkin1.service.SupportedRegionDescription;
import org.github.akarkin1.tg.TgRequestContext;

import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public final class SupportedRegionCommand implements BotCommand<TextCommandResponse> {

  private final NodeService nodeService;
  private final Authorizer authorizer;

  @Override
  public TextCommandResponse run(List<String> args) {
    Set<String> allowedServices = authorizer.getAllowedServices(TgRequestContext.getUsername());
    List<SupportedRegionDescription> regionDescriptions = nodeService.getSupportedRegionDescriptions()
        .stream()
        .filter(desc -> desc.getServices().stream().anyMatch(allowedServices::contains))
        .toList();
    StringBuilder responseBuilder = new StringBuilder();
    if (regionDescriptions.isEmpty()) {
      responseBuilder.append("${command.supported-regions.no-supported-regions.message}");
    } else {
      responseBuilder.append("${command.supported-regions.supported-regions.message}: ").append("\n");

      regionDescriptions.forEach(regionDescription ->
                            responseBuilder.append("\t– %s".formatted(regionDescription.toString()))
                                .append("\n"));
    }


    return new TextCommandResponse(responseBuilder.toString());
  }

  @Override
  public String getDescription() {
    return "${command.supported-regions.description.message}";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.SUPPORTED_REGIONS);
  }

}
