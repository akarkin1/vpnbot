package org.github.akarkin1.ui.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.Authorizer;
import org.github.akarkin1.auth.Permission;
import org.github.akarkin1.ecs.TaskInfo;
import org.github.akarkin1.tailscale.NodeOwner;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.ui.NodeLauncher;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.NodeNotifications;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.LaunchScreens;

import java.util.List;
import java.util.Map;

@Log4j2
@RequiredArgsConstructor
public class LaunchController implements NodeLauncher {

  private final TailscaleNodeService nodeService;
  private final Authorizer authorizer;
  private final UiMessenger messenger;
  private final LaunchScreens launchScreens;
  private final NodeNotifications nodeNotifications;

  public void launch(UiContext context, Integer messageId, String regionId) {
    launch(context, messageId, regionId, true);
  }

  public void launchAnother(UiContext context, Integer messageId, String regionId) {
    launch(context, messageId, regionId, false);
  }

  private void launch(UiContext context, Integer messageId, String regionId,
                      boolean offerExistingNodes) {
    String username = context.username();
    if (username == null || !authorizer.hasPermission(username, Permission.RUN_NODES)) {
      messenger.edit(context, messageId, launchScreens.notAllowed());
      return;
    }

    messenger.edit(context, messageId, launchScreens.starting(regionId));
    if (!isRegionSupported(context, messageId, regionId)) {
      return;
    }

    if (offerExistingNodes) {
      List<TaskInfo> existing;
      try {
        existing = ownNodesInRegion(username, regionId);
      } catch (RuntimeException e) {
        log.error("Failed to list nodes in region {} for user {}", regionId, username, e);
        messenger.edit(context, messageId, launchScreens.failed(regionId));
        return;
      }
      if (!existing.isEmpty()) {
        messenger.edit(context, messageId, launchScreens.existingNodes(regionId, existing));
        return;
      }
    }

    startNode(context, messageId, regionId, null);
  }

  @Override
  public void launchInNewMessage(UiContext context, String regionId, String hostName) {
    Integer messageId = messenger.send(context, launchScreens.starting(regionId));
    if (isRegionSupported(context, messageId, regionId)) {
      startNode(context, messageId, regionId, hostName);
    }
  }

  private boolean isRegionSupported(UiContext context, Integer messageId, String regionId) {
    if (!nodeService.getSupportedRegionIds().contains(regionId)) {
      messenger.edit(context, messageId, launchScreens.regionUnavailable());
      return false;
    }
    return true;
  }

  private List<TaskInfo> ownNodesInRegion(String username, String regionId) {
    return nodeService.listTasks(username)
        .stream()
        .filter(node -> username.equals(node.getRunBy()))
        .filter(node -> node.getRegion() != null && regionId.equals(node.getRegion().id()))
        .toList();
  }

  private void startNode(UiContext context, Integer messageId, String regionId, String hostName) {
    Map<String, String> environment = nodeNotifications.build(context, messageId, regionId);
    NodeOwner owner = new NodeOwner(context.username(), context.chatId(), context.languageCode());
    try {
      nodeService.runNode(regionId, owner, hostName, environment);
    } catch (RuntimeException e) {
      log.error("Failed to run a node in region {} for user {}", regionId, context.username(), e);
      messenger.edit(context, messageId, launchScreens.failed(regionId));
      return;
    }

    messenger.edit(context, messageId, launchScreens.waiting(regionId));
  }

}
