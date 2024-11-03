package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.UserAction;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.tg.TgUserContext;

import java.util.List;

@RequiredArgsConstructor
public final class SupportedRegionCommand implements BotCommand<TextCommandResponse> {

  private final TailscaleNodeService tailscaleNodeService;
  private final Authorizer authorizer;

  @Override
  public TextCommandResponse run(List<String> args) {
    if (!authorizer.isAllowed(TgUserContext.getUsername(), UserAction.SUPPORTED_REGIONS)) {
      return new TextCommandResponse("You are not authorized to run this command.");
    }

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

}
