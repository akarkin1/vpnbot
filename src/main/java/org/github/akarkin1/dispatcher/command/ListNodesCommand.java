package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;
import org.github.akarkin1.ecs.EcsNode;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.tg.TgRequestContext;

import java.util.List;

@RequiredArgsConstructor
public final class ListNodesCommand implements BotCommand<TextCommandResponse> {

  private final TailscaleNodeService tailscaleNodeService;
  private final Authorizer authorizer;

  @Override
  public TextCommandResponse run(List<String> args) {
    TgUser user = TgRequestContext.getUser();

    if (user == null || user.id() == null) {
      return new TextCommandResponse("${command.list-nodes.no-permission}");
    }

    // root: no owner restriction is required, just list all nodes running
    String ownerId = authorizer.hasPermission(user, Permission.ROOT_ACCESS)
        ? null
        : String.valueOf(user.id());

    List<EcsNode> ecsNodes = tailscaleNodeService.listTasks(ownerId);

    if (ecsNodes.isEmpty()) {
      return new TextCommandResponse("${command.list-nodes.no-nodes-run.message}");
    }

    StringBuilder responseBuilder = new StringBuilder();
    responseBuilder.append("${command.list-nodes.running-nodes.message}: ").append("\n");

    ecsNodes.forEach(taskInfo ->
                          responseBuilder.append("\t– ${common.node.name.message}: %s%n".formatted(taskInfo.getHostName()))
                              .append("\t\t${common.node.status.message}: %s%n".formatted(taskInfo.getState()))
                              .append("\t\t${common.node.public-ip.message}: %s%n".formatted(taskInfo.getPublicIp()))
                              .append("\t\t${common.node.region.message}: %s (%s)%n".formatted(taskInfo.getLocation(), taskInfo.getRegion())));

    return new TextCommandResponse(responseBuilder.toString());
  }

  @Override
  public String getDescription() {
    return "${command.list-nodes.description.message}";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.LIST_NODES);
  }
}
