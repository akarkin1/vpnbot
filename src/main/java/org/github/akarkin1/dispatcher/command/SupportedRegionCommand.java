package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;
import org.github.akarkin1.tailscale.TailscaleNodeService;

import java.util.List;

@RequiredArgsConstructor
public final class SupportedRegionCommand implements BotCommand<TextCommandResponse> {

  private final TailscaleNodeService tailscaleNodeService;

  @Override
  public TextCommandResponse run(List<String> args) {
    List<String> regionDescriptions = tailscaleNodeService.getSupportedRegionDescriptions();
    StringBuilder responseBuilder = new StringBuilder();
    responseBuilder.append("${command.supported-regions.supported-regions.message}: ").append("\n");

    regionDescriptions.forEach(regionDescription ->
                          responseBuilder.append("\t– %s".formatted(regionDescription))
                              .append("\n"));

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
