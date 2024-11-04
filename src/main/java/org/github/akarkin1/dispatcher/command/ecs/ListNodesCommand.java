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
      return new TextCommandResponse("Action is not allowed.");
    }

    if (authorizer.hasPermission(username, Permission.ROOT_ACCESS)) {
      // no username restriction is required, just list all nodes running
      username = null;
    }

    List<TaskInfo> taskInfos = tailscaleNodeService.listTasks(username);

    if (taskInfos.isEmpty()) {
      return new TextCommandResponse("No nodes run.");
    }

    StringBuilder responseBuilder = new StringBuilder();
    responseBuilder.append("Running nodes: ").append("\n");

    taskInfos.forEach(taskInfo ->
                          responseBuilder.append("\t– Node Name: %s%n".formatted(taskInfo.getHostName()))
                              .append("\t\tNode Status: %s%n".formatted(taskInfo.getState()))
                              .append("\t\tPublic IP: %s%n".formatted(taskInfo.getPublicIp()))
                              .append("\t\tRegion: %s (%s)%n".formatted(taskInfo.getLocation(), taskInfo.getRegion())));

    return new TextCommandResponse(responseBuilder.toString());
  }

  @Override
  public String getDescription() {
    return "Shows the list of Tailscale nodes run by the user.";
  }

  @Override
  public List<Permission> getRequiredPermissions() {
    return List.of(Permission.LIST_NODES);
  }
}
