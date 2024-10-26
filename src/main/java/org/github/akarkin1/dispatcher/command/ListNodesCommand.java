package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.auth.Authenticator;
import org.github.akarkin1.auth.UserAction;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.tg.TgUserContext;

import java.util.List;

@RequiredArgsConstructor
public final class ListNodesCommand implements BotCommand<TextCommandResponse> {

  private final TailscaleNodeService tailscaleNodeService;
  private final Authenticator authenticator;

  @Override
  public TextCommandResponse run(List<String> args) {
    String username = TgUserContext.getUsername();

    if (username == null) {
      return new TextCommandResponse("Action is not allowed.");
    }

    if (authenticator.isAllowed(username, UserAction.ADMIN)) {
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
}
