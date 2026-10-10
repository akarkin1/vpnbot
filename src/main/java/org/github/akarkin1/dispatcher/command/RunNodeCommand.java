package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.response.EmptyResponse;
import org.github.akarkin1.message.MessageConsumer;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.tg.TgRequestContext;
import org.github.akarkin1.ui.NodeLauncher;
import org.github.akarkin1.ui.UiContext;

import java.util.List;

@RequiredArgsConstructor
public final class RunNodeCommand implements BotCommand<EmptyResponse> {

  private final TailscaleNodeService tailscaleNodeService;
  private final MessageConsumer messageConsumer;
  private final NodeLauncher nodeLauncher;

  @Override
  public EmptyResponse run(List<String> args) {
    if (args.isEmpty()) {
      messageConsumer.accept(
        "${command.run-node.missing-arg.region.error}. ${common.command.description.message}: "
        + getDescription());
      return EmptyResponse.NONE;
    }

    String userRegion = args.getFirst();

    if (!tailscaleNodeService.isRegionValid(userRegion)) {
      messageConsumer.accept(
        "${common.region.invalid-name.error}",
        userRegion);
      return EmptyResponse.NONE;
    }

    if (!tailscaleNodeService.isRegionSupported(userRegion)) {
      messageConsumer.accept("${common.region.not-supported.error}",
                             userRegion);
      return EmptyResponse.NONE;
    }

    String userHost = null;
    if (args.size() > 1) {
      userHost = args.get(1);
      if (!tailscaleNodeService.isHostnameAvailable(userRegion, userHost)) {
        messageConsumer.accept(
          "${command.run-node.node.name-is-incorrect-or-in-use.error}",
          userHost);
        return EmptyResponse.NONE;
      }
    }

    UiContext context = new UiContext(TgRequestContext.getChatId(),
                                      TgRequestContext.getUsername(),
                                      null,
                                      TgRequestContext.getLanguageCode());
    nodeLauncher.launchInNewMessage(context, tailscaleNodeService.toRegionId(userRegion), userHost);
    return EmptyResponse.NONE;
  }

  @Override
  public String getDescription() {
    return "${command.run-node.description.message}";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.RUN_NODES);
  }

}
