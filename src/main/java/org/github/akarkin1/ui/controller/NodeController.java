package org.github.akarkin1.ui.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.github.akarkin1.auth.TgUser;
import org.github.akarkin1.ecs.EcsNode;
import org.github.akarkin1.tailscale.TailscaleNodeService;
import org.github.akarkin1.ui.NodeRef;
import org.github.akarkin1.ui.UiContext;
import org.github.akarkin1.ui.messenger.UiMessenger;
import org.github.akarkin1.ui.screen.LaunchScreens;
import org.github.akarkin1.ui.screen.NodeScreens;

import java.util.Optional;
import java.util.function.BiPredicate;

@Log4j2
@RequiredArgsConstructor
public class NodeController {

  private final TailscaleNodeService nodeService;
  private final NodeAccess nodeAccess;
  private final UiMessenger messenger;
  private final NodeScreens nodeScreens;
  private final LaunchScreens launchScreens;

  public void use(UiContext context, Integer messageId, NodeRef node) {
    findNode(context, messageId, node, nodeAccess::canView)
        .ifPresent(task -> messenger.edit(context, messageId, launchScreens.ready(
            task.getHostName(), node.regionId(), task.getPublicIp(), node.taskId())));
  }

  public void stop(UiContext context, Integer messageId, NodeRef node) {
    Optional<EcsNode> task = findNode(context, messageId, node, nodeAccess::canStop);
    if (task.isEmpty()) {
      return;
    }

    if (nodeAccess.needsConfirmation(context.user(), task.get())) {
      messenger.edit(context, messageId, nodeScreens.confirmStop(task.get()));
      return;
    }

    stopNode(context, messageId, node, task.get());
  }

  public void confirmStop(UiContext context, Integer messageId, NodeRef node) {
    Optional<EcsNode> task = findNode(context, messageId, node, nodeAccess::canStop);
    if (task.isEmpty()) {
      return;
    }

    stopNode(context, messageId, node, task.get());
    if (nodeAccess.needsConfirmation(context.user(), task.get())) {
      notifyOwner(task.get());
    }
  }

  /**
   * Checks the user and the region before any AWS call, then fetches the node from ECS.
   * Edits the message and returns empty when the node is gone or not accessible.
   */
  private Optional<EcsNode> findNode(UiContext context, Integer messageId, NodeRef node,
                                     BiPredicate<TgUser, EcsNode> access) {
    if (!nodeAccess.canManageNodes(context.user())) {
      messenger.edit(context, messageId, nodeScreens.notAllowed());
      return Optional.empty();
    }
    if (!nodeService.getSupportedRegionIds().contains(node.regionId())) {
      messenger.edit(context, messageId, nodeScreens.alreadyStopped());
      return Optional.empty();
    }

    Optional<EcsNode> task = nodeService.getNode(node.regionId(), node.taskId());
    if (task.isEmpty()) {
      messenger.edit(context, messageId, nodeScreens.alreadyStopped());
      return Optional.empty();
    }
    if (!access.test(context.user(), task.get())) {
      messenger.edit(context, messageId, nodeScreens.notAllowed());
      return Optional.empty();
    }
    return task;
  }

  private void stopNode(UiContext context, Integer messageId, NodeRef node, EcsNode task) {
    String reason = "Stopped by %s via the bot".formatted(stoppedBy(context));
    messenger.edit(context, messageId, nodeScreens.stopping(task));
    nodeService.stopNode(node.regionId(), node.taskId(), reason);
  }

  private void notifyOwner(EcsNode task) {
    Long ownerChatId = parseId(task.getChatId());
    if (ownerChatId == null) {
      log.info("Node {} has no valid owner chat id ({}), the owner is not notified",
               task.getId(), task.getChatId());
      return;
    }

    UiContext owner = new UiContext(ownerChatId, parseId(task.getRunBy()), task.getRunByName(),
                                    null, task.getLanguageCode());
    try {
      messenger.send(owner, nodeScreens.stoppedByAdmin(task));
    } catch (RuntimeException e) {
      log.error("Failed to notify the owner of node {} in chat {}", task.getId(), ownerChatId, e);
    }
  }

  private static String stoppedBy(UiContext context) {
    return context.username() != null ? "@" + context.username() : String.valueOf(context.userId());
  }

  private static Long parseId(String id) {
    try {
      return id == null ? null : Long.valueOf(id);
    } catch (NumberFormatException e) {
      return null;
    }
  }

}
