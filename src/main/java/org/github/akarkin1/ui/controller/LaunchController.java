package org.github.akarkin1.ui.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.ecs.RunTaskStatus;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.LaunchScreens;
import org.github.akarkin1.ui.screen.Screen;

@Log4j2
@RequiredArgsConstructor
public class LaunchController {

  private final TailscaleNodeService nodeService;
  private final Authorizer authorizer;
  private final UiMessenger messenger;
  private final LaunchScreens launchScreens;

  public void launch(UiContext context, Integer messageId, String regionId) {
    String username = context.username();
    if (username == null || !authorizer.hasPermission(username, Permission.RUN_NODES)) {
      messenger.edit(context, messageId, launchScreens.notAllowed());
      return;
    }

    if (!nodeService.getSupportedRegionIds().contains(regionId)) {
      messenger.edit(context, messageId, launchScreens.regionUnavailable());
      return;
    }

    messenger.edit(context, messageId, launchScreens.starting(regionId));
    TaskInfo task;
    try {
      task = nodeService.runNode(regionId, username, null);
    } catch (RuntimeException e) {
      log.error("Failed to run a node in region {} for user {}", regionId, username, e);
      messenger.edit(context, messageId, launchScreens.failed(regionId));
      return;
    }

    messenger.edit(context, messageId, launchScreens.waiting(regionId));
    RunTaskStatus status;
    try {
      status = nodeService.checkNodeStatus(task);
    } catch (RuntimeException e) {
      log.error("Failed to check the status of node {} in region {}", task.getId(), regionId, e);
      messenger.edit(context, messageId, launchScreens.failed(regionId));
      return;
    }

    messenger.edit(context, messageId, resultScreen(task, status, regionId));
  }

  private Screen resultScreen(TaskInfo task, RunTaskStatus status, String regionId) {
    return switch (status) {
      case HEALTHY -> nodeService.getFullTaskInfo(task.getRegion(), task.getCluster(), task.getId())
          .map(launchScreens::ready)
          .orElseGet(() -> launchScreens.stillStarting(regionId));
      case UNKNOWN -> launchScreens.stillStarting(regionId);
      case UNHEALTHY -> launchScreens.failed(regionId);
    };
  }

}
