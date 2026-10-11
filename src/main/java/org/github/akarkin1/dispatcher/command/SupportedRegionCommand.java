package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.screen.Screen;

import java.util.List;

@RequiredArgsConstructor
public final class SupportedRegionCommand implements BotCommand {

  private final TailscaleNodeService tailscaleNodeService;

  @Override
  public Screen run(UiContext context, List<String> args) {
    List<String> regionDescriptions = tailscaleNodeService.getSupportedRegionDescriptions();
    StringBuilder responseBuilder = new StringBuilder();
    if (regionDescriptions.isEmpty()) {
      responseBuilder.append("${command.supported-regions.no-supported-regions.message}");
    } else {
      responseBuilder.append("${command.supported-regions.supported-regions.message}: ").append("\n");
      // one line per region; the descriptions are params, so they are HTML-escaped
      responseBuilder.append("\t– %s\n".repeat(regionDescriptions.size()));
    }

    return BotCommand.reply(responseBuilder.toString(), regionDescriptions.toArray());
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
