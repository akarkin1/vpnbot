package org.github.akarkin1.dispatcher.command.ecs;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.UserPermission;
import org.github.akarkin1.dispatcher.command.TextCommandResponse;
import org.github.akarkin1.tailscale.TailscaleNodeService;

import java.util.List;

@RequiredArgsConstructor
public final class SupportedRegionCommand implements BotCommandV2<TextCommandResponse> {

  private final TailscaleNodeService tailscaleNodeService;

  @Override
  public TextCommandResponse run(List<String> args) {
    List<String> regionDescriptions = tailscaleNodeService.getSupportedRegionDescriptions();
    StringBuilder responseBuilder = new StringBuilder();
    responseBuilder.append("Supported regions: ").append("\n");

    regionDescriptions.forEach(regionDescription ->
                          responseBuilder.append("\t– %s".formatted(regionDescription))
                              .append("\n"));

    return new TextCommandResponse(responseBuilder.toString());
  }

  @Override
  public String getDescription() {
    return "Shows list of available regions, where Tailscale infrastructure is set up.";
  }

  @Override
  public List<UserPermission> getRequiredPermissions() {
    return List.of(UserPermission.SUPPORTED_REGIONS);
  }

}
