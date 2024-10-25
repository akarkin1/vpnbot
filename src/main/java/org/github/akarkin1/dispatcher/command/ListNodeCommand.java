package org.github.akarkin1.dispatcher.command;

import lombok.RequiredArgsConstructor;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.tg.TgUserContext;

import java.util.List;

@RequiredArgsConstructor
public final class ListNodeCommand implements BotCommand<TextCommandResponse> {

  private final TailscaleNodeService tailscaleNodeService;

  @Override
  public TextCommandResponse run(List<String> args) {
    List<TaskInfo> taskInfos = tailscaleNodeService.listTasks(TgUserContext.getUsername());
    StringBuilder responseBuilder = new StringBuilder();
    responseBuilder.append("Running nodes: ").append("\n");

    taskInfos.forEach(taskInfo ->
                          responseBuilder.append("\t– Node Name: %s".formatted(taskInfo.getHostName()))
                              .append("\t  Public IP: %s".formatted(taskInfo.getPublicIp()))
                              .append("\t  Region: %s (%s)".formatted(taskInfo.getLocation(), taskInfo.getRegion()))
                              .append("\n"));

    return new TextCommandResponse(responseBuilder.toString());
  }

  @Override
  public String getDescription() {
    return "Shows the list of Tailscale nodes run by the user.";
  }
}
