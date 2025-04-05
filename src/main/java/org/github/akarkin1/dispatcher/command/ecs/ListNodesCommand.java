package org.github.akarkin1.dispatcher.command.ecs;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.command.TextCommandResponse;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.tg.TgRequestContext;

import java.util.List;

@RequiredArgsConstructor
public final class ListNodesCommand implements BotCommandV2<TextCommandResponse> {

  private final TailscaleNodeService tailscaleNodeService;
  private final Authorizer authorizer;

  @Override
  public TextCommandResponse run(List<String> args) {
    String username = TgRequestContext.getUsername();

    if (username == null) {
      return new TextCommandResponse("${command.list-nodes.no-permission}");
    }

    if (authorizer.hasPermission(username, Permission.ROOT_ACCESS)) {
      // no username restriction is required, just list all nodes running
      username = null;
    }

    List<TaskInfo> taskInfos = tailscaleNodeService.listTasks(username);

    if (taskInfos.isEmpty()) {
      return new TextCommandResponse("${command.list-nodes.no-nodes-run.message}");
    }

    StringBuilder responseBuilder = new StringBuilder();
    responseBuilder.append("${command.list-nodes.running-nodes.message}: ").append("\n");

    taskInfos.forEach(taskInfo ->
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
