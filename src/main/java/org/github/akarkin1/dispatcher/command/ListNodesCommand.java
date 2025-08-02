package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.dispatcher.response.TextCommandResponse;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.service.NodeService;
import org.github.akarkin1.tg.TgRequestContext;

import java.util.List;
import java.util.Set;

@RequiredArgsConstructor
public final class ListNodesCommand implements BotCommand<TextCommandResponse> {

  private final NodeService nodeService;
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

    Set<String> allowedServices = authorizer.getAllowedServices(username, Permission.ROOT_ACCESS);

    List<TaskInfo> outputTasks = nodeService.listTasks(username)
        .stream()
        .filter(task -> allowedServices.contains(task.getServiceName()))
        .toList();

    if (outputTasks.isEmpty()) {
      return new TextCommandResponse("${command.list-nodes.no-nodes-run.message}");
    }

    StringBuilder responseBuilder = new StringBuilder();
    responseBuilder.append("${command.list-nodes.running-nodes.message}: ").append("\n");

    outputTasks.forEach(taskInfo ->
                          responseBuilder.append("\t– ${common.node.name.message}: %s%n".formatted(taskInfo.getHostName()))
                              .append("\t\t${common.node.type.message}: %s%n".formatted(taskInfo.getServiceName()))
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
