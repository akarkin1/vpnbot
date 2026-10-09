package org.github.akarkin1.ui.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.apache.commons.lang3.StringUtils;
import org.github.akarkin1.ecs.TaskInfo;
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

  private static final String DEFAULT_LANGUAGE_CODE = "en-US";

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
    Optional<TaskInfo> task = findNode(context, messageId, node, nodeAccess::canStop);
    if (task.isEmpty()) {
      return;
    }

    if (nodeAccess.needsConfirmation(context.username(), task.get())) {
      messenger.edit(context, messageId, nodeScreens.confirmStop(task.get()));
      return;
    }

    stopNode(context, messageId, node, task.get());
  }

  public void confirmStop(UiContext context, Integer messageId, NodeRef node) {
    Optional<TaskInfo> task = findNode(context, messageId, node, nodeAccess::canStop);
    if (task.isEmpty()) {
      return;
    }

    stopNode(context, messageId, node, task.get());
    if (nodeAccess.needsConfirmation(context.username(), task.get())) {
      notifyOwner(task.get());
    }
  }

  /** Fetches the node from ECS; edits the message and returns empty when it is gone or not accessible. */
  private Optional<TaskInfo> findNode(UiContext context, Integer messageId, NodeRef node,
                                      BiPredicate<String, TaskInfo> access) {
    Optional<TaskInfo> task = nodeService.getNode(node.regionId(), node.taskId());
    if (task.isEmpty()) {
      messenger.edit(context, messageId, nodeScreens.alreadyStopped());
      return Optional.empty();
    }
    if (!access.test(context.username(), task.get())) {
      messenger.edit(context, messageId, nodeScreens.notAllowed());
      return Optional.empty();
    }
    return task;
  }

  private void stopNode(UiContext context, Integer messageId, NodeRef node, TaskInfo task) {
    String reason = "Stopped by @%s via the bot".formatted(context.username());
    nodeService.stopNode(node.regionId(), node.taskId(), reason);
    messenger.edit(context, messageId, nodeScreens.stopping(task));
  }

  private void notifyOwner(TaskInfo task) {
    Long ownerChatId = parseChatId(task.getChatId());
    if (ownerChatId == null) {
      log.info("Node {} has no valid owner chat id ({}), the owner is not notified",
               task.getId(), task.getChatId());
      return;
    }

    String languageCode = StringUtils.defaultIfBlank(task.getLanguageCode(), DEFAULT_LANGUAGE_CODE);
    UiContext owner = new UiContext(ownerChatId, task.getRunBy(), null, languageCode);
    try {
      messenger.send(owner, nodeScreens.stoppedByAdmin(task));
    } catch (RuntimeException e) {
      log.error("Failed to notify the owner of node {} in chat {}", task.getId(), ownerChatId, e);
    }
  }

  private static Long parseChatId(String chatId) {
    try {
      return chatId == null ? null : Long.valueOf(chatId);
    } catch (NumberFormatException e) {
      return null;
    }
  }

}
